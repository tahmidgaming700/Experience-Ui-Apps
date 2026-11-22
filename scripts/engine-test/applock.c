/* applock: run a program under Android's app seccomp filter (test tool).
   Mirrors zygote: PR_SET_NO_NEW_PRIVS, then check arch, load the syscall
   number, run the generated allow table, SECCOMP_RET_TRAP otherwise.
   Build (after extract-app-seccomp-filter.py wrote app_filter_arm64.h):
     clang --target=aarch64-linux-gnu -fuse-ld=lld -O2 -static -nostdlib \
       -ffreestanding -fno-stack-protector -fno-builtin -o applock applock.c
   Usage on the device: applock /path/to/program args... */
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <linux/audit.h>
#include <linux/prctl.h>
#include <asm/unistd.h>
#include "app_filter_arm64.h"

static long sys(long n, long a, long b, long c, long d) {
  register long x8 __asm__("x8") = n;
  register long x0 __asm__("x0") = a;
  register long x1 __asm__("x1") = b;
  register long x2 __asm__("x2") = c;
  register long x3 __asm__("x3") = d;
  __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2), "r"(x3) : "memory");
  return x0;
}
static unsigned long len(const char *s) { unsigned long n = 0; while (s[n]) n++; return n; }
static void err(const char *s) { sys(__NR_write, 2, (long)s, len(s), 0); }

#define N (4 + APP_TABLE_LEN + 1)
static struct sock_filter prog[N];

__attribute__((used)) void cmain(long *sp) {
  long argc = sp[0];
  char **argv = (char **)(sp + 1);
  char **envp = argv + argc + 1;
  if (argc < 2) { err("usage: applock prog [args]\n"); sys(__NR_exit_group, 2, 0, 0, 0); }
  int i = 0;
  prog[i++] = (struct sock_filter)BPF_STMT(BPF_LD | BPF_W | BPF_ABS, 4); /* arch */
  prog[i++] = (struct sock_filter)BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AUDIT_ARCH_AARCH64, 1, 0);
  prog[i++] = (struct sock_filter)BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP);
  prog[i++] = (struct sock_filter)BPF_STMT(BPF_LD | BPF_W | BPF_ABS, 0); /* nr */
  for (int j = 0; j < APP_TABLE_LEN; j++) prog[i++] = app_table[j];
  prog[i++] = (struct sock_filter)BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP);
  struct sock_fprog fp = {.len = (unsigned short)i, .filter = prog};
  if (sys(__NR_prctl, PR_SET_NO_NEW_PRIVS, 1, 0, 0) != 0) { err("applock: no_new_privs failed\n"); sys(__NR_exit_group, 1, 0, 0, 0); }
  if (sys(__NR_seccomp, SECCOMP_SET_MODE_FILTER, 0, (long)&fp, 0) != 0) { err("applock: seccomp failed\n"); sys(__NR_exit_group, 1, 0, 0, 0); }
  long r = sys(__NR_execve, (long)argv[1], (long)(argv + 1), (long)envp, 0);
  err("applock: execve failed\n");
  sys(__NR_exit_group, (int)-r, 0, 0, 0);
}
__attribute__((naked)) void _start(void) { __asm__("mov x0, sp\n bl cmain\n brk #0\n"); }

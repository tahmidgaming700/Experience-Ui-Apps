import struct,sys
d=open(sys.argv[1],'rb').read()
g=d.find(struct.pack('<I',0x616c4467)); mx,slots,lbs=struct.unpack_from('<III',d,g+40)
print('geometry max_meta',mx,'slots',slots,'lbs',lbs)
h=d.find(struct.pack('<I',0x414C5030)); maj,mnr,hs=struct.unpack_from('<HHI',d,h+4)
tbl=h+hs; o=h+8+4+32+4+32
desc=[struct.unpack_from('<III',d,o+12*i) for i in range(4)]
P=[d[tbl+desc[0][0]+i*desc[0][2]:][:desc[0][2]] for i in range(desc[0][1])]
E=[struct.unpack_from('<QIQI',d,tbl+desc[1][0]+i*desc[1][2]) for i in range(desc[1][1])]
G=[d[tbl+desc[2][0]+i*desc[2][2]:][:desc[2][2]] for i in range(desc[2][1])]
B=[d[tbl+desc[3][0]+i*desc[3][2]:][:desc[3][2]] for i in range(desc[3][1])]
print('version',maj,mnr)
for b in B:
  fl,al,ao,sz=struct.unpack_from('<QIIQ',b); print('blockdev',b[24:60].rstrip(b'\0').decode(),'size',sz,'first_sector',fl,'align',al)
for x in G:
  print('group',x[:36].rstrip(b'\0').decode(),'flags',struct.unpack_from('<I',x,36)[0],'max',struct.unpack_from('<Q',x,40)[0])
for p in P:
  n=p[:36].rstrip(b'\0').decode(); a,fe,ne,gi=struct.unpack_from('<IIII',p,36)
  print('part',n,'attr',a,'group',gi,'size',sum(E[fe+i][0]*512 for i in range(ne)))

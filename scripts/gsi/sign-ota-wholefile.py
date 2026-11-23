#!/usr/bin/env python3
"""Sign a recovery OTA zip with the AOSP whole-file signature (as SignApk --whole-file).

AOSP/LineageOS recovery verifies a sideloaded package by:
  signed_data = file[0 : len - comment_size - 2]      (whole file except the EOCD comment
                                                        and the 2-byte comment-length field)
  footer      = file[-6:]  = [signature_start:2][0xff 0xff][comment_size:2]  (little-endian)
  signature   = file[len - signature_start : len - 6]  = a PKCS#7 SignedData whose single
                SignerInfo has no signed attributes; its encryptedDigest is RSA-PKCS1v15(
                SHA-256(signed_data)). Recovery extracts that encryptedDigest and runs
                RSA_verify(SHA-256, SHA-256(signed_data)) against each trusted key.
(See bootable/recovery/install/verifier.cpp verify_file + read_pkcs7.)

This reproduces that format and self-verifies it the same way before writing the output.

  sign-ota-wholefile.py --in unsigned.zip --out signed.zip --key testkey.pk8 --cert testkey.x509.pem
"""
import argparse
import struct
import sys

from asn1crypto import cms, x509, algos, core
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import pkcs12, load_der_private_key


def load_key(pk8_path):
    der = open(pk8_path, "rb").read()
    return load_der_private_key(der, password=None)


def build_pkcs7(cert_der, signature):
    """SignedData with one SignerInfo, no signed attrs, encryptedDigest = signature."""
    cert = x509.Certificate.load(cert_der)
    sha256 = algos.DigestAlgorithm({"algorithm": "sha256"})
    sinfo = cms.SignerInfo({
        "version": "v1",
        "sid": cms.SignerIdentifier({
            "issuer_and_serial_number": cms.IssuerAndSerialNumber({
                "issuer": cert.issuer,
                "serial_number": cert.serial_number,
            })
        }),
        "digest_algorithm": sha256,
        # No signed_attrs: recovery skips exactly 4 fields then reads the OCTET STRING.
        "signature_algorithm": algos.SignedDigestAlgorithm({"algorithm": "rsassa_pkcs1v15"}),
        "signature": signature,
    })
    signed_data = cms.SignedData({
        "version": "v1",
        "digest_algorithms": cms.DigestAlgorithms([sha256]),
        "encap_content_info": cms.ContentInfo({"content_type": "data"}),
        "certificates": cms.CertificateSet([cms.CertificateChoices({"certificate": cert})]),
        "signer_infos": cms.SignerInfos([sinfo]),
    })
    return cms.ContentInfo({"content_type": "signed_data", "content": signed_data}).dump()


def self_verify(final, cert_der):
    """Mirror verifier.cpp verify_file against the produced bytes."""
    length = len(final)
    footer = final[length - 6:]
    assert footer[2] == 0xff and footer[3] == 0xff, "footer magic"
    comment_size = footer[4] | (footer[5] << 8)
    signature_start = footer[0] | (footer[1] << 8)
    assert signature_start <= comment_size and signature_start > 6
    eocd_size = comment_size + 22
    eocd = final[length - eocd_size:]
    assert eocd[0:4] == b"\x50\x4b\x05\x06", "EOCD magic"
    signed_len = length - eocd_size + 22 - 2
    signed_data = final[:signed_len]
    sig_block = final[length - signature_start: length - 6]
    ci = cms.ContentInfo.load(sig_block)
    sinfo = ci["content"]["signer_infos"][0]
    enc = sinfo["signature"].native
    cert = x509.Certificate.load(cert_der)
    from cryptography.x509 import load_der_x509_certificate
    pub = load_der_x509_certificate(cert_der).public_key()
    h = hashes.Hash(hashes.SHA256())
    h.update(signed_data)
    digest = h.finalize()
    from cryptography.hazmat.primitives.asymmetric import utils as asym_utils
    pub.verify(enc, signed_data, padding.PKCS1v15(), hashes.SHA256())
    return comment_size, signature_start, signed_len


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--in", dest="inp", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--key", required=True, help="DER PKCS#8 private key (.pk8)")
    ap.add_argument("--cert", required=True, help="PEM or DER X.509 cert")
    a = ap.parse_args()

    base = open(a.inp, "rb").read()
    if base[-22:-20] != b"PK\x05\x06"[:2] and b"PK\x05\x06" not in base[-65558:]:
        sys.exit("input is not a zip (no EOCD)")
    # Python writes EOCD with an empty comment, so the last 2 bytes are the comment-length (0x0000).
    if base[-2:] != b"\x00\x00":
        sys.exit("input zip must have an empty archive comment")

    cert_pem = open(a.cert, "rb").read()
    if b"-----BEGIN" in cert_pem:
        from cryptography.x509 import load_pem_x509_certificate
        cert_der = load_pem_x509_certificate(cert_pem).public_bytes(serialization.Encoding.DER)
    else:
        cert_der = cert_pem

    signed_data = base[:-2]
    key = load_key(a.key)
    signature = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    sig_block = build_pkcs7(cert_der, signature)
    comment_size = len(sig_block) + 6
    footer = struct.pack("<H", comment_size) + b"\xff\xff" + struct.pack("<H", comment_size)
    final = signed_data + struct.pack("<H", comment_size) + sig_block + footer

    cs, ss, sl = self_verify(final, cert_der)
    assert sl == len(signed_data), (sl, len(signed_data))
    open(a.out, "wb").write(final)
    print(f"[ota-sign] signed; comment={cs} sig_block={len(sig_block)} signed_len={sl} total={len(final)}")
    print("[ota-sign] self-verification passed (mirrors recovery verifier.cpp)")


if __name__ == "__main__":
    main()

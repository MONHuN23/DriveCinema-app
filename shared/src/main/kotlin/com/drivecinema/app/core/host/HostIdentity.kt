package com.drivecinema.app.core.host

import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date

/** The PC host's TLS identity: an EC P-256 key and a self-signed certificate that clients pin at pairing. */
class HostIdentity(val privateKey: PrivateKey, val certificate: X509Certificate) {
    /** What clients pin: SHA-256 of the certificate, lowercase hex. */
    val fingerprint: String get() = fingerprintOf(certificate)

    /** A PKCS#12 key store holding the identity, for `KeyManagerFactory` and for saving it. */
    fun toKeyStore(password: CharArray): KeyStore = KeyStore.getInstance("PKCS12").apply {
        load(null, null)
        setKeyEntry(ALIAS, privateKey, password, arrayOf<Certificate>(certificate))
    }

    companion object {
        const val ALIAS = "drivecinema-host"
        private const val VALID_YEARS = 20L

        fun fingerprintOf(certificate: Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }

        fun fromKeyStore(keyStore: KeyStore, password: CharArray): HostIdentity? {
            val key = keyStore.getKey(ALIAS, password) as? PrivateKey ?: return null
            val cert = keyStore.getCertificate(ALIAS) as? X509Certificate ?: return null
            return HostIdentity(key, cert)
        }

        /**
         * A new self-signed certificate, built from Bouncy Castle's ASN.1 classes (in `bcprov`, already a dependency) and
         * signed with the JDK, so no certificate library (`bcpkix`) is needed. Trust comes from pinning, not from a CA,
         * so the name and long validity don't matter.
         */
        fun generate(now: Long = System.currentTimeMillis(), random: SecureRandom = SecureRandom()): HostIdentity {
            val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()
            val name = X500Name("CN=DriveCinema PC")
            val signatureAlgorithm = AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256)
            val tbs = V3TBSCertificateGenerator().apply {
                setSerialNumber(ASN1Integer(BigInteger(63, random).add(BigInteger.ONE)))
                setIssuer(name)
                setSubject(name)
                setStartDate(Time(Date(now - 24L * 3600 * 1000)))
                setEndDate(Time(Date(now + VALID_YEARS * 365 * 24 * 3600 * 1000)))
                setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(keyPair.public.encoded))
                setSignature(signatureAlgorithm)
            }.generateTBSCertificate()
            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign(keyPair.private)
                update(tbs.encoded)
                sign()
            }
            val der = DERSequence(ASN1EncodableVector().apply {
                add(tbs)
                add(signatureAlgorithm)
                add(DERBitString(signature))
            }).encoded
            val certificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
            certificate.verify(keyPair.public)
            return HostIdentity(keyPair.private, certificate)
        }
    }
}

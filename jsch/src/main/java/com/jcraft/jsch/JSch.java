/* -*-mode:java; c-basic-offset:2; indent-tabs-mode:nil -*- */
/*
Copyright (c) 2002-2018 ymnk, JCraft,Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

  1. Redistributions of source code must retain the above copyright notice,
     this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright 
     notice, this list of conditions and the following disclaimer in 
     the documentation and/or other materials provided with the distribution.

  3. The names of the authors may not be used to endorse or promote products
     derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.jcraft.jsch;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JSch {
    /**
     * The version number.
     */
    public static final String VERSION = BuildConfig.VERSION_NAME;

    private static final Map<String, String> config = new HashMap<>();

    static {
        config.put("kex", Util.getEnvProperty("jsch.kex", "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,diffie-hellman-group14-sha256"));
        config.put("server_host_key", Util.getEnvProperty("jsch.server_host_key", "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256"));
        config.put("prefer_known_host_key_types", Util.getEnvProperty("jsch.prefer_known_host_key_types", "yes"));
        config.put("enable_server_sig_algs", Util.getEnvProperty("jsch.enable_server_sig_algs", "yes"));
        config.put("cipher.s2c", Util.getEnvProperty("jsch.cipher", "aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com"));
        config.put("cipher.c2s", Util.getEnvProperty("jsch.cipher", "aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com"));
        config.put("mac.s2c", Util.getEnvProperty("jsch.mac", "hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com,hmac-sha1-etm@openssh.com,hmac-sha2-256,hmac-sha2-512,hmac-sha1"));
        config.put("mac.c2s", Util.getEnvProperty("jsch.mac", "hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com,hmac-sha1-etm@openssh.com,hmac-sha2-256,hmac-sha2-512,hmac-sha1"));
        config.put("compression.s2c", Util.getEnvProperty("jsch.compression", "none"));
        config.put("compression.c2s", Util.getEnvProperty("jsch.compression", "none"));

        config.put("lang.s2c", Util.getEnvProperty("jsch.lang", ""));
        config.put("lang.c2s", Util.getEnvProperty("jsch.lang", ""));

        config.put("dhgex_min", Util.getEnvProperty("jsch.dhgex_min", "2048"));
        config.put("dhgex_max", Util.getEnvProperty("jsch.dhgex_max", "8192"));
        config.put("dhgex_preferred", Util.getEnvProperty("jsch.dhgex_preferred", "3072"));

        config.put("compression_level", Util.getEnvProperty("jsch.compression_level", "6"));

        config.put("diffie-hellman-group-exchange-sha1",
                "com.jcraft.jsch.DHGEX1");
        config.put("diffie-hellman-group1-sha1",
                "com.jcraft.jsch.DHG1");
        config.put("diffie-hellman-group14-sha1",
                "com.jcraft.jsch.DHG14"); // available since JDK 8
        config.put("diffie-hellman-group-exchange-sha256",
                "com.jcraft.jsch.DHGEX256"); // available since JDK 1.4.2
        config.put("diffie-hellman-group-exchange-sha224@ssh.com",
                "com.jcraft.jsch.DHGEX224");
        config.put("diffie-hellman-group-exchange-sha384@ssh.com",
                "com.jcraft.jsch.DHGEX384");
        config.put("diffie-hellman-group-exchange-sha512@ssh.com",
                "com.jcraft.jsch.DHGEX512");
        config.put("diffie-hellman-group14-sha256",
                "com.jcraft.jsch.DHG14256");
        config.put("diffie-hellman-group15-sha512",
                "com.jcraft.jsch.DHG15");
        config.put("diffie-hellman-group16-sha512",
                "com.jcraft.jsch.DHG16");
        config.put("diffie-hellman-group17-sha512",
                "com.jcraft.jsch.DHG17");
        config.put("diffie-hellman-group18-sha512",
                "com.jcraft.jsch.DHG18");
        config.put("diffie-hellman-group14-sha256@ssh.com",
                "com.jcraft.jsch.DHG14256");
        config.put("diffie-hellman-group14-sha224@ssh.com",
                "com.jcraft.jsch.DHG14224");
        config.put("diffie-hellman-group15-sha256@ssh.com",
                "com.jcraft.jsch.DHG15256");
        config.put("diffie-hellman-group15-sha384@ssh.com",
                "com.jcraft.jsch.DHG15384");
        config.put("diffie-hellman-group16-sha512@ssh.com",
                "com.jcraft.jsch.DHG16");
        config.put("diffie-hellman-group16-sha384@ssh.com",
                "com.jcraft.jsch.DHG16384");
        config.put("diffie-hellman-group18-sha512@ssh.com",
                "com.jcraft.jsch.DHG18");
        config.put("ecdsa-sha2-nistp256", "com.jcraft.jsch.jce.SignatureECDSA256");
        config.put("ecdsa-sha2-nistp384", "com.jcraft.jsch.jce.SignatureECDSA384");
        config.put("ecdsa-sha2-nistp521", "com.jcraft.jsch.jce.SignatureECDSA521");

        config.put("ecdh-sha2-nistp256", "com.jcraft.jsch.DHEC256");
        config.put("ecdh-sha2-nistp384", "com.jcraft.jsch.DHEC384");
        config.put("ecdh-sha2-nistp521", "com.jcraft.jsch.DHEC521");

        config.put("ecdh-sha2-nistp", "com.jcraft.jsch.jce.ECDHN");

        config.put("curve25519-sha256", "com.jcraft.jsch.DH25519");
        config.put("curve25519-sha256@libssh.org", "com.jcraft.jsch.DH25519");
        config.put("curve448-sha512", "com.jcraft.jsch.DH448");

        config.put("dh", "com.jcraft.jsch.jce.DH");
        config.put("3des-cbc", "com.jcraft.jsch.jce.TripleDESCBC");
        config.put("blowfish-cbc", "com.jcraft.jsch.jce.BlowfishCBC");
        config.put("hmac-sha1", "com.jcraft.jsch.jce.HMACSHA1");
        config.put("hmac-sha1-96", "com.jcraft.jsch.jce.HMACSHA196");
        config.put("hmac-sha2-256", "com.jcraft.jsch.jce.HMACSHA256");
        config.put("hmac-sha2-512", "com.jcraft.jsch.jce.HMACSHA512");
        config.put("hmac-md5", "com.jcraft.jsch.jce.HMACMD5");
        config.put("hmac-md5-96", "com.jcraft.jsch.jce.HMACMD596");
        config.put("hmac-sha1-etm@openssh.com", "com.jcraft.jsch.jce.HMACSHA1ETM");
        config.put("hmac-sha1-96-etm@openssh.com", "com.jcraft.jsch.jce.HMACSHA196ETM");
        config.put("hmac-sha2-256-etm@openssh.com", "com.jcraft.jsch.jce.HMACSHA256ETM");
        config.put("hmac-sha2-512-etm@openssh.com", "com.jcraft.jsch.jce.HMACSHA512ETM");
        config.put("hmac-md5-etm@openssh.com", "com.jcraft.jsch.jce.HMACMD5ETM");
        config.put("hmac-md5-96-etm@openssh.com", "com.jcraft.jsch.jce.HMACMD596ETM");
        config.put("hmac-sha256-2@ssh.com", "com.jcraft.jsch.jce.HMACSHA2562SSHCOM");
        config.put("hmac-sha224@ssh.com", "com.jcraft.jsch.jce.HMACSHA224SSHCOM");
        config.put("hmac-sha256@ssh.com", "com.jcraft.jsch.jce.HMACSHA256SSHCOM");
        config.put("hmac-sha384@ssh.com", "com.jcraft.jsch.jce.HMACSHA384SSHCOM");
        config.put("hmac-sha512@ssh.com", "com.jcraft.jsch.jce.HMACSHA512SSHCOM");
        config.put("sha-1", "com.jcraft.jsch.jce.SHA1");
        config.put("sha-224", "com.jcraft.jsch.jce.SHA224");
        config.put("sha-256", "com.jcraft.jsch.jce.SHA256");
        config.put("sha-384", "com.jcraft.jsch.jce.SHA384");
        config.put("sha-512", "com.jcraft.jsch.jce.SHA512");
        config.put("md5", "com.jcraft.jsch.jce.MD5");
        config.put("sha1", "com.jcraft.jsch.jce.SHA1");
        config.put("sha224", "com.jcraft.jsch.jce.SHA224");
        config.put("sha256", "com.jcraft.jsch.jce.SHA256");
        config.put("sha384", "com.jcraft.jsch.jce.SHA384");
        config.put("sha512", "com.jcraft.jsch.jce.SHA512");
        config.put("signature.dss", "com.jcraft.jsch.jce.SignatureDSA");
        config.put("ssh-rsa", "com.jcraft.jsch.jce.SignatureRSA");
        config.put("rsa-sha2-256", "com.jcraft.jsch.jce.SignatureRSASHA256");
        config.put("rsa-sha2-512", "com.jcraft.jsch.jce.SignatureRSASHA512");
        config.put("ssh-rsa-sha224@ssh.com", "com.jcraft.jsch.jce.SignatureRSASHA224SSHCOM");
        config.put("ssh-rsa-sha256@ssh.com", "com.jcraft.jsch.jce.SignatureRSASHA256SSHCOM");
        config.put("ssh-rsa-sha384@ssh.com", "com.jcraft.jsch.jce.SignatureRSASHA384SSHCOM");
        config.put("ssh-rsa-sha512@ssh.com", "com.jcraft.jsch.jce.SignatureRSASHA512SSHCOM");
        config.put("keypairgen.dsa", "com.jcraft.jsch.jce.KeyPairGenDSA");
        config.put("keypairgen.rsa", "com.jcraft.jsch.jce.KeyPairGenRSA");
        config.put("keypairgen.ecdsa", "com.jcraft.jsch.jce.KeyPairGenECDSA");
        config.put("random", "com.jcraft.jsch.jce.Random");

        config.put("none", "com.jcraft.jsch.CipherNone");

        config.put("aes128-gcm@openssh.com", "com.jcraft.jsch.jce.AES128GCM");
        config.put("aes256-gcm@openssh.com", "com.jcraft.jsch.jce.AES256GCM");

        config.put("aes128-cbc", "com.jcraft.jsch.jce.AES128CBC");
        config.put("aes192-cbc", "com.jcraft.jsch.jce.AES192CBC");
        config.put("aes256-cbc", "com.jcraft.jsch.jce.AES256CBC");
        config.put("rijndael-cbc@lysator.liu.se", "com.jcraft.jsch.jce.AES256CBC");

        config.put("aes128-ctr", "com.jcraft.jsch.jce.AES128CTR");
        config.put("aes192-ctr", "com.jcraft.jsch.jce.AES192CTR");
        config.put("aes256-ctr", "com.jcraft.jsch.jce.AES256CTR");
        config.put("3des-ctr", "com.jcraft.jsch.jce.TripleDESCTR");
        config.put("blowfish-ctr", "com.jcraft.jsch.jce.BlowfishCTR");
        config.put("arcfour", "com.jcraft.jsch.jce.ARCFOUR");
        config.put("arcfour128", "com.jcraft.jsch.jce.ARCFOUR128");
        config.put("arcfour256", "com.jcraft.jsch.jce.ARCFOUR256");

        config.put("userauth.none", "com.jcraft.jsch.UserAuthNone");
        config.put("userauth.password", "com.jcraft.jsch.UserAuthPassword");
        config.put("userauth.keyboard-interactive", "com.jcraft.jsch.UserAuthKeyboardInteractive");
        config.put("userauth.publickey", "com.jcraft.jsch.UserAuthPublicKey");

        config.put("zlib", "com.jcraft.jsch.jzlib.Compression");
        config.put("zlib@openssh.com", "com.jcraft.jsch.jzlib.Compression");

        config.put("pbkdf", "com.jcraft.jsch.jce.PBKDF");

        config.put("StrictHostKeyChecking", "ask");
        config.put("HashKnownHosts", "no");

        config.put("PreferredAuthentications", Util.getEnvProperty("jsch.preferred_authentications", "publickey,keyboard-interactive,password"));
        config.put("PubkeyAcceptedAlgorithms", Util.getEnvProperty("jsch.client_pubkey", "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256"));

        config.put("CheckCiphers", Util.getEnvProperty("jsch.check_ciphers", "aes256-ctr,aes192-ctr,aes128-ctr,aes256-cbc,aes192-cbc,aes128-cbc,3des-ctr,arcfour,arcfour128,arcfour256"));
        config.put("CheckMacs", Util.getEnvProperty("jsch.check_macs", "hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com,hmac-sha1-etm@openssh.com,hmac-sha2-256,hmac-sha2-512"));
        config.put("CheckKexes", Util.getEnvProperty("jsch.check_kexes", "curve25519-sha256,curve25519-sha256@libssh.org,curve448-sha512,diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,diffie-hellman-group14-sha256,diffie-hellman-group14-sha1,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521"));
        config.put("CheckSignatures", Util.getEnvProperty("jsch.check_signatures", "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521"));
        config.put("FingerprintHash", Util.getEnvProperty("jsch.fingerprint_hash", "sha256"));

        config.put("MaxAuthTries", Util.getEnvProperty("jsch.max_auth_tries", "6"));
        config.put("ClearAllForwardings", "no");
    }

    /**
     * Key exchange algorithms implemented by the library.
     */
    public static final Set<String> implementedKexSet =
            Collections.unmodifiableSet(
                    Util.setOf(
                            "diffie-hellman-group1-sha1",
                            "diffie-hellman-group14-sha1",
                            "diffie-hellman-group14-sha256",
                            "diffie-hellman-group16-sha512",
                            "diffie-hellman-group18-sha512",
                            "diffie-hellman-group-exchange-sha1",
                            "diffie-hellman-group-exchange-sha256",
                            "ecdh-sha2-nistp256",
                            "ecdh-sha2-nistp384",
                            "ecdh-sha2-nistp521",
                            "curve448-sha512",
                            "curve25519-sha256",
                            "curve25519-sha256@libssh.org",
                            "sntrup4591761x25519-sha512@tinyssh.org",

                            "diffie-hellman-group-exchange-sha224@ssh.com",
                            "diffie-hellman-group-exchange-sha384@ssh.com",
                            "diffie-hellman-group-exchange-sha512@ssh.com",
                            "diffie-hellman-group15-sha512",
                            "diffie-hellman-group17-sha512",
                            "diffie-hellman-group14-sha256@ssh.com",
                            "diffie-hellman-group14-sha224@ssh.com",
                            "diffie-hellman-group15-sha256@ssh.com",
                            "diffie-hellman-group15-sha384@ssh.com",
                            "diffie-hellman-group16-sha512@ssh.com",
                            "diffie-hellman-group16-sha384@ssh.com",
                            "diffie-hellman-group18-sha512@ssh.com"
                    ));
    /**
     * Key exchange algorithms supported by a runtime.
     */
    public static final Set<String> supportedKexSet =
            Collections.unmodifiableSet(
                    Util.filter(kex -> Session.checkKex(JSch.getConfig(kex)),
                            new HashSet<>(implementedKexSet)));

    /**
     * Encryption algorithms implemented by the library.
     */
    public static final Set<String> implementedCipherSet =
            Collections.unmodifiableSet(
                    Util.setOf(
                            "3des-cbc",
                            "aes128-cbc",
                            "aes192-cbc",
                            "aes256-cbc",
                            "rijndael-cbc@lysator.liu.se",
                            "aes128-ctr",
                            "aes192-ctr",
                            "aes256-ctr",
                            "aes128-gcm@openssh.com",
                            "aes256-gcm@openssh.com",
                            "chacha20-poly1305@openssh.com",

                            "blowfish-cbc",
                            "3des-ctr",
                            "blowfish-ctr",
                            "arcfour",
                            "arcfour128",
                            "arcfour256"
                    ));
    /**
     * Encryption algorithms supported by a runtime.
     */
    public static final Set<String> supportedCipherSet =
            Collections.unmodifiableSet(
                    Util.filter(cipher -> Session.checkCipher(JSch.getConfig(cipher)),
                            new HashSet<>(implementedCipherSet)));

    /**
     * Message authentication code algorithms implemented by the library.
     */
    public static final Set<String> implementedMacSet =
            Collections.unmodifiableSet(
                    Util.setOf(
                            "hmac-sha1",
                            "hmac-sha1-96",
                            "hmac-sha2-256",
                            "hmac-sha2-512",
                            "hmac-md5",
                            "hmac-md5-96",
                            "umac-64@openssh.com",
                            "umac-128@openssh.com",
                            "hmac-sha1-etm@openssh.com",
                            "hmac-sha1-96-etm@openssh.com",
                            "hmac-sha2-256-etm@openssh.com",
                            "hmac-sha2-512-etm@openssh.com",
                            "hmac-md5-etm@openssh.com",
                            "hmac-md5-96-etm@openssh.com",
                            "umac-64-etm@openssh.com",
                            "umac-128-etm@openssh.com",

                            "hmac-sha256-2@ssh.com",
                            "hmac-sha224@ssh.com",
                            "hmac-sha256@ssh.com",
                            "hmac-sha384@ssh.com",
                            "hmac-sha512@ssh.com"
                    ));
    /**
     * Message authentication code algorithms supported by a runtime.
     */
    public static final Set<String> supportedMacSet =
            Collections.unmodifiableSet(
                    Util.filter(mac -> Session.checkMac(JSch.getConfig(mac)),
                            new HashSet<>(implementedMacSet)));

    /**
     * Signature algorithms implemented by the library.
     */
    public static final Set<String> implementedSigSet =
            Collections.unmodifiableSet(
                    Util.setOf(
                            "ssh-ed25519",
                            "ssh-ed25519-cert-v01@openssh.com",
                            "sk-ssh-ed25519@openssh.com",
                            "sk-ssh-ed25519-cert-v01@openssh.com",
                            "ssh-rsa",
                            "rsa-sha2-256",
                            "rsa-sha2-512",
                            "ssh-dss",
                            "ecdsa-sha2-nistp256",
                            "ecdsa-sha2-nistp384",
                            "ecdsa-sha2-nistp521",
                            "sk-ecdsa-sha2-nistp256@openssh.com",
                            "webauthn-sk-ecdsa-sha2-nistp256@openssh.com",
                            "ssh-rsa-cert-v01@openssh.com",
                            "rsa-sha2-256-cert-v01@openssh.com",
                            "rsa-sha2-512-cert-v01@openssh.com",
                            "ssh-dss-cert-v01@openssh.com",
                            "ecdsa-sha2-nistp256-cert-v01@openssh.com",
                            "ecdsa-sha2-nistp384-cert-v01@openssh.com",
                            "ecdsa-sha2-nistp521-cert-v01@openssh.com",
                            "sk-ecdsa-sha2-nistp256-cert-v01@openssh.com"
                    ));
    /**
     * Signature algorithms supported by a runtime.
     */
    public static final Set<String> supportedSigSet =
            Collections.unmodifiableSet(
                    Util.filter(sig -> Session.checkSignature(JSch.getConfig(sig)),
                            new HashSet<>(implementedSigSet)));

    /**
     * Public key algorithms implemented by the library.
     */
    public static final Set<String> implementedKeySet =
            Collections.unmodifiableSet(
                    Util.setOf(
                            "ssh-ed25519",
                            "ssh-ed25519-cert-v01@openssh.com",
                            "sk-ssh-ed25519@openssh.com",
                            "sk-ssh-ed25519-cert-v01@openssh.com",
                            "ssh-rsa",
                            "ssh-dss",
                            "ecdsa-sha2-nistp256",
                            "ecdsa-sha2-nistp384",
                            "ecdsa-sha2-nistp521",
                            "sk-ecdsa-sha2-nistp256@openssh.com",
                            "ssh-rsa-cert-v01@openssh.com",
                            "ssh-dss-cert-v01@openssh.com",
                            "ecdsa-sha2-nistp256-cert-v01@openssh.com",
                            "ecdsa-sha2-nistp384-cert-v01@openssh.com",
                            "ecdsa-sha2-nistp521-cert-v01@openssh.com",
                            "sk-ecdsa-sha2-nistp256-cert-v01@openssh.com",

                            "rsa-sha2-256",
                            "rsa-sha2-512"
                    ));
    /**
     * Public key algorithms supported by a runtime.
     */
    public static final Set<String> supportedKeySet =
            Collections.unmodifiableSet(
                    Util.filter(supportedSigSet::contains,
                            new HashSet<>(implementedKeySet)));

    /**
     * Authentication types implemented by the library.
     */
    public static final Set<String> implementedAuthTypes =
            Collections.unmodifiableSet(Util.setOf(
                    "none",
                    "keyboard-interactive",
                    "password",
                    "publickey"
            ));
    /**
     * Authentication types supported by a runtime.
     */
    public static final Set<String> supportedAuthTypes = implementedAuthTypes;

    private final List<Session> sessionPool = new ArrayList<>();

    private final IdentityRepository defaultIdentityRepository =
            new LocalIdentityRepository(this);

    private IdentityRepository identityRepository = defaultIdentityRepository;

    private ConfigRepository configRepository = null;

    /**
     * Sets the {@code identityRepository}, which will be referred
     * in the public key authentication.
     *
     * @param identityRepository if {@code null} is given,
     *                           the default repository, which usually refers to ~/.ssh/, will be used.
     * @see #getIdentityRepository()
     */
    public synchronized void setIdentityRepository(final IdentityRepository identityRepository) {
        if (identityRepository == null) {
            this.identityRepository = defaultIdentityRepository;
        } else {
            this.identityRepository = identityRepository;
        }
    }

    public synchronized IdentityRepository getIdentityRepository() {
        return this.identityRepository;
    }

    public ConfigRepository getConfigRepository() {
        return this.configRepository;
    }

    public void setConfigRepository(final ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    private HostKeyRepository known_hosts = null;

    public static final Logger NULL_LOGGER = new Logger() {
        @Override
        public boolean isEnabled(final int level) {
            return false;
        }

        @Override
        public void log(final int level, final String message) {
        }
    };
    static Logger logger = NULL_LOGGER;
    private Logger instLogger;

    public JSch() {
    }

    /**
     * Instantiates the {@code Session} object with
     * {@code host}.  The user name and port number will be retrieved from
     * ConfigRepository.  If user name is not given,
     * the system property "user.name" will be referred.
     *
     * @param host hostname
     * @return the instance of {@code Session} class.
     * @throws JSchException if {@code username} or {@code host} are invalid.
     * @see #getSession(String username, String host, int port)
     * @see com.jcraft.jsch.Session
     * @see com.jcraft.jsch.ConfigRepository
     */
    public Session getSession(final String host)
            throws JSchException {
        return getSession(null, host, 22);
    }

    /**
     * Instantiates the {@code Session} object with
     * {@code username} and {@code host}.
     * The TCP port 22 will be used in making the connection.
     * Note that the TCP connection must not be established
     * until Session#connect().
     *
     * @param username user name
     * @param host     hostname
     * @return the instance of {@code Session} class.
     * @throws JSchException if {@code username} or {@code host} are invalid.
     * @see #getSession(String username, String host, int port)
     * @see com.jcraft.jsch.Session
     */
    public Session getSession(final String username, final String host)
            throws JSchException {
        return getSession(username, host, 22);
    }

    /**
     * Instantiates the {@code Session} object with given
     * {@code username}, {@code host} and {@code port}.
     * Note that the TCP connection must not be established
     * until Session#connect().
     *
     * @param username user name
     * @param host     hostname
     * @param port     port number
     * @return the instance of {@code Session} class.
     * @throws JSchException if {@code username} or {@code host} are invalid.
     * @see #getSession(String username, String host, int port)
     * @see com.jcraft.jsch.Session
     */
    public Session getSession(final String username, final String host, final int port)
            throws JSchException {
        if (host == null) {
            throw new JSchException("host must not be null.");
        }
        return new Session(this, username, host, port);
    }

    protected void addSession(final Session session) {
        synchronized (sessionPool) {
            sessionPool.add(session);
        }
    }

    protected boolean removeSession(final Session session) {
        synchronized (sessionPool) {
            return sessionPool.remove(session);
        }
    }

    /**
     * Sets the hostkey repository.
     *
     * @param hkrepo
     * @see com.jcraft.jsch.HostKeyRepository
     * @see com.jcraft.jsch.KnownHosts
     */
    public void setHostKeyRepository(final HostKeyRepository hkrepo) {
        known_hosts = hkrepo;
    }

    /**
     * Sets the instance of {@code KnownHosts}, which refers
     * to {@code filename}.
     *
     * @param filename filename of known_hosts file.
     * @throws JSchException if the given filename is invalid.
     * @see com.jcraft.jsch.KnownHosts
     */
    public void setKnownHosts(final String filename) throws JSchException {
        if (known_hosts == null) known_hosts = new KnownHosts(this);
        if (known_hosts instanceof KnownHosts) {
            synchronized (known_hosts) {
                ((KnownHosts) known_hosts).setKnownHosts(filename);
            }
        }
    }

    /**
     * Sets the instance of {@code KnownHosts} generated with
     * {@code stream}.
     *
     * @param stream the instance of InputStream from known_hosts file.
     * @throws JSchException if an I/O error occurs.
     * @see com.jcraft.jsch.KnownHosts
     */
    public void setKnownHosts(final InputStream stream) throws JSchException {
        if (known_hosts == null) known_hosts = new KnownHosts(this);
        if (known_hosts instanceof KnownHosts) {
            synchronized (known_hosts) {
                ((KnownHosts) known_hosts).setKnownHosts(stream);
            }
        }
    }

    /**
     * Returns the current hostkey repository.
     * By the default, this method will the instance of {@code KnownHosts}.
     *
     * @return current hostkey repository.
     * @see com.jcraft.jsch.HostKeyRepository
     * @see com.jcraft.jsch.KnownHosts
     */
    public HostKeyRepository getHostKeyRepository() {
        if (known_hosts == null)
            known_hosts = new KnownHosts(this);
        return known_hosts;
    }

    /**
     * Sets the private key, which will be referred in
     * the public key authentication.
     *
     * @param prvkey filename of the private key.
     * @throws JSchException if {@code prvkey} is invalid.
     * @see #addIdentity(String prvkey, String passphrase)
     */
    public void addIdentity(final String prvkey) throws JSchException {
        addIdentity(prvkey, (byte[]) null);
    }

    /**
     * Sets the private key, which will be referred in
     * the public key authentication.
     * Before registering it into identityRepository,
     * it will be deciphered with {@code passphrase}.
     *
     * @param prvkey     filename of the private key.
     * @param passphrase passphrase for {@code prvkey}.
     * @throws JSchException if {@code passphrase} is not right.
     * @see #addIdentity(String prvkey, byte[] passphrase)
     */
    public void addIdentity(final String prvkey, final String passphrase) throws JSchException {
        byte[] _passphrase = null;
        if (passphrase != null) {
            _passphrase = Util.str2byte(passphrase);
        }
        addIdentity(prvkey, _passphrase);
        if (_passphrase != null)
            Util.bzero(_passphrase);
    }

    /**
     * Sets the private key, which will be referred in
     * the public key authentication.
     * Before registering it into identityRepository,
     * it will be deciphered with {@code passphrase}.
     *
     * @param prvkey     filename of the private key.
     * @param passphrase passphrase for {@code prvkey}.
     * @throws JSchException if {@code passphrase} is not right.
     * @see #addIdentity(String prvkey, String pubkey, byte[] passphrase)
     */
    public void addIdentity(final String prvkey, final byte[] passphrase) throws JSchException {
        addIdentity(IdentityFile.newInstance(prvkey, null, this), passphrase);
    }

    /**
     * Sets the private key, which will be referred in
     * the public key authentication.
     * Before registering it into identityRepository,
     * it will be deciphered with {@code passphrase}.
     *
     * @param prvkey     filename of the private key.
     * @param pubkey     filename of the public key.
     * @param passphrase passphrase for {@code prvkey}.
     * @throws JSchException if {@code passphrase} is not right.
     */
    public void addIdentity(final String prvkey, final String pubkey, final byte[] passphrase)
            throws JSchException {
        addIdentity(IdentityFile.newInstance(prvkey, pubkey, this), passphrase);
    }

    /**
     * Sets the private key, which will be referred in
     * the public key authentication.
     * Before registering it into identityRepository,
     * it will be deciphered with {@code passphrase}.
     *
     * @param name       name of the identity to be used to
     *                   retrieve it in the identityRepository.
     * @param prvkey     private key in byte array.
     * @param pubkey     public key in byte array.
     * @param passphrase passphrase for {@code prvkey}.
     */
    public void addIdentity(final String name,
                            final byte[] prvkey, final byte[] pubkey, final byte[] passphrase)
            throws JSchException {
        addIdentity(IdentityFile.newInstance(name, prvkey, pubkey, this), passphrase);
    }

    /**
     * Sets the private key, which will be referred in
     * the public key authentication.
     * Before registering it into identityRepository,
     * it will be deciphered with {@code passphrase}.
     *
     * @param identity   private key.
     * @param passphrase passphrase for {@code identity}.
     * @throws JSchException if {@code passphrase} is not right.
     */
    public void addIdentity(final Identity identity, byte[] passphrase)
            throws JSchException {
        if (passphrase != null) {
            try {
                final byte[] goo = new byte[passphrase.length];
                System.arraycopy(passphrase, 0, goo, 0, passphrase.length);
                passphrase = goo;
                identity.setPassphrase(passphrase);
            } finally {
                Util.bzero(passphrase);
            }
        }

        if (identityRepository instanceof LocalIdentityRepository) {
            ((LocalIdentityRepository) identityRepository).add(identity);
        } else if (identity instanceof IdentityFile && !identity.isEncrypted()) {
            identityRepository.add(((IdentityFile) identity).getKeyPair().forSSHAgent());
        } else {
            synchronized (this) {
                if (!(identityRepository instanceof IdentityRepositoryWrapper)) {
                    setIdentityRepository(new IdentityRepositoryWrapper(identityRepository));
                }
            }
            ((IdentityRepositoryWrapper) identityRepository).add(identity);
        }
    }

    /**
     * @deprecated use #removeIdentity(Identity identity)
     */
    @Deprecated
    public void removeIdentity(final String name) throws JSchException {
        final List<Identity> identities = identityRepository.getIdentities();
        for (final Identity identity : identities) {
            if (!identity.getName().equals(name))
                continue;
            if (identityRepository instanceof LocalIdentityRepository) {
                ((LocalIdentityRepository) identityRepository).remove(identity);
            } else
                identityRepository.remove(identity.getPublicKeyBlob());
        }
    }

    /**
     * Removes the identity from identityRepository.
     *
     * @param identity the indentity to be removed.
     * @throws JSchException if {@code identity} is invalid.
     */
    public void removeIdentity(final Identity identity) throws JSchException {
        identityRepository.remove(identity.getPublicKeyBlob());
    }

    /**
     * Lists names of identities included in the identityRepository.
     *
     * @return names of identities
     * @throws JSchException if identityReposory has problems.
     */
    public List<String> getIdentityNames() throws JSchException {
        final List<Identity> identities = identityRepository.getIdentities();
        final List<String> r = new ArrayList<>(identities.size());
        for (final Identity identity : identities)
            r.add(identity.getName());
        return r;
    }

    /**
     * Removes all identities from identityRepository.
     *
     * @throws JSchException if identityReposory has problems.
     */
    public void removeAllIdentity() throws JSchException {
        identityRepository.removeAll();
    }

    static String _getInternalKey(final String key) {
        switch (key) {
            case "PubkeyAcceptedKeyTypes":
                return "PubkeyAcceptedAlgorithms";
            default:
                return key;
        }
    }

    /**
     * Returns the config value for the specified key.
     *
     * @param key key for the configuration.
     * @return config value
     */
    public static String getConfig(final String key) {
        synchronized (config) {
            return config.get(_getInternalKey(key));
        }
    }

    /**
     * Sets or Overrides the configuration.
     *
     * @param newConf configurations
     */
    public static void setConfig(final Map<String, String> newConf) {
        synchronized (config) {
            for (final Map.Entry<String, String> entry : newConf.entrySet()) {
                config.put(_getInternalKey(entry.getKey()), entry.getValue());
            }
        }
    }

    /**
     * Sets or Overrides the configuration.
     *
     * @param key   key for the configuration
     * @param value value for the configuration
     */
    public static void setConfig(final String key, final String value) {
        synchronized (config) {
            config.put(_getInternalKey(key), value);
        }
    }

    /**
     * Sets the logger
     *
     * @param logger logger or {@code null} if no logging
     *               should take place
     * @see com.jcraft.jsch.Logger
     */
    public static void setLogger(final Logger logger) {
        if (logger == null)
            JSch.logger = NULL_LOGGER;
        else
            JSch.logger = logger;
    }

    /**
     * Returns  a logger to be used for this particular instance of JSch
     *
     * @return The logger that is used by this instance. If no particular
     * logger has been set, the statically set logger is returned.
     */
    public Logger getInstanceLogger() {
        if (this.instLogger == null) {
            return logger;
        }
        return instLogger;
    }

    /**
     * Sets a logger to be used for this particular instance of JSch
     *
     * @param logger The logger to be used or {@code null} if
     *               the statically set logger should be used
     */
    public void setInstanceLogger(final Logger logger) {
        this.instLogger = logger;
    }

    /**
     * Returns the statically set logger, i.e. the logger being
     * used by all JSch instances without explicitly set logger.
     *
     * @return The logger
     */
    public static Logger getLogger() {
        return logger;
    }
}

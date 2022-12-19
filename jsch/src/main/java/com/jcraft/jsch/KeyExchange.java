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

import androidx.annotation.NonNull;

public abstract class KeyExchange {

    static final int PROPOSAL_KEX_ALGS = 0;
    static final int PROPOSAL_SERVER_HOST_KEY_ALGS = 1;
    static final int PROPOSAL_ENC_ALGS_CTOS = 2;
    static final int PROPOSAL_ENC_ALGS_STOC = 3;
    static final int PROPOSAL_MAC_ALGS_CTOS = 4;
    static final int PROPOSAL_MAC_ALGS_STOC = 5;
    static final int PROPOSAL_COMP_ALGS_CTOS = 6;
    static final int PROPOSAL_COMP_ALGS_STOC = 7;
    static final int PROPOSAL_LANG_CTOS = 8;
    static final int PROPOSAL_LANG_STOC = 9;
    static final int PROPOSAL_NUM = 10;

    private static final String[] PROPOSAL_NAMES = new String[]{
            "kex",
            "server_host_key",
            "cipher.c2s",
            "cipher.s2c",
            "mac.c2s",
            "mac.s2c",
            "compression.c2s",
            "compression.s2c",
            "lang.c2s",
            "lang.s2c"
    };

    @NonNull
    static String getAlgorithmNameByProposalIndex(final int index) {
        if (index < 0 || index >= PROPOSAL_NAMES.length)
            return "";
        return PROPOSAL_NAMES[index];
    }

    //static String kex_algs="diffie-hellman-group-exchange-sha1"+
    //                       ",diffie-hellman-group1-sha1";

    //static String kex="diffie-hellman-group-exchange-sha1";
    static String kex = "diffie-hellman-group1-sha1";
    static String server_host_key = "ssh-rsa,ssh-dss";
    static String enc_c2s = "blowfish-cbc";
    static String enc_s2c = "blowfish-cbc";
    static String mac_c2s = "hmac-md5";     // hmac-md5,hmac-sha1,hmac-ripemd160,
    // hmac-sha1-96,hmac-md5-96
    static String mac_s2c = "hmac-md5";
    //static String comp_c2s="none";        // zlib
//static String comp_s2c="none";
    static String lang_c2s = "";
    static String lang_s2c = "";

    public static final int STATE_END = 0;

    protected Session session = null;
    protected HASH sha = null;
    protected byte[] K = null;
    protected byte[] H = null;
    protected byte[] K_S = null;

    /**
     * Checks the implementation availability
     *
     * @throws JSchException on failure
     */
    public abstract void check(Configuration cfg) throws JSchException;

    public void init(final Session session,
                     final byte[] V_S, final byte[] V_C, final byte[] I_S, final byte[] I_C)
            throws Exception {
        this.session = session;
    }

    public abstract boolean next(Buffer buf) throws Exception;

    public abstract int getState();

    protected static final int RSA = 0;
    protected static final int DSS = 1;
    protected static final int ECDSA = 2;
    protected static final int EDDSA = 3;
    private int type = 0;
    private String key_alg_name = "";

    public String getKeyType() {
        if (type == DSS) return "DSA";
        if (type == RSA) return "RSA";
        if (type == EDDSA) return "EDDSA";
        return "ECDSA";
    }

    public String getKeyAlgorithmName() {
        return key_alg_name;
    }

    @NonNull
    protected static String[] guess(@NonNull final Session session,
                                    @NonNull final byte[] I_S, @NonNull final byte[] I_C)
            throws Exception {
        final String[] guess = new String[PROPOSAL_NUM];
        final Buffer sb = new Buffer(I_S);
        sb.setOffSet(17);
        final Buffer cb = new Buffer(I_C);
        cb.setOffSet(17);

        if (session.getLogger().isEnabled(Logger.INFO)) {
            for (int i = 0; i < PROPOSAL_NUM; i++) {
                session.getLogger().log(Logger.INFO,
                        "kex: server: " + Util.byte2str(sb.getString()));
            }
            for (int i = 0; i < PROPOSAL_NUM; i++) {
                session.getLogger().log(Logger.INFO,
                        "kex: client: " + Util.byte2str(cb.getString()));
            }
            sb.setOffSet(17);
            cb.setOffSet(17);
        }

        for (int i = 0; i < PROPOSAL_NUM; i++) {
            final byte[] sp = sb.getString();  // server proposal
            final byte[] cp = cb.getString();  // client proposal
            int j = 0;
            int k = 0;

            loop:
            while (j < cp.length) {
                while (j < cp.length && cp[j] != ',') j++;
                if (k == j)
                    throw new JSchAlgoNegoFailException(i, Util.byte2str(cp), Util.byte2str(sp));
                final String algorithm = Util.byte2str(cp, k, j - k);
                int l = 0;
                int m = 0;
                while (l < sp.length) {
                    while (l < sp.length && sp[l] != ',') l++;
                    if (m == l)
                        throw new JSchAlgoNegoFailException(i, Util.byte2str(cp), Util.byte2str(sp));
                    if (algorithm.equals(Util.byte2str(sp, m, l - m))) {
                        guess[i] = algorithm;
                        break loop;
                    }
                    l++;
                    m = l;
                }
                j++;
                k = j;
            }
            if (j == 0)
                guess[i] = "";
            else if (guess[i] == null)
                throw new JSchAlgoNegoFailException(i, Util.byte2str(cp), Util.byte2str(sp));
        }

        final boolean _s2cAEAD;
        final boolean _c2sAEAD;
        try {
            final Class<? extends Cipher> _s2cclazz =
                    Class.forName(session.getConfig(guess[PROPOSAL_ENC_ALGS_STOC]))
                            .asSubclass(Cipher.class);
            final Cipher _s2ccipher = _s2cclazz.getDeclaredConstructor().newInstance();
            _s2cAEAD = _s2ccipher.isAEAD();
            if (_s2cAEAD) {
                guess[PROPOSAL_MAC_ALGS_STOC] = null;
            }

            final Class<? extends Cipher> _c2sclazz =
                    Class.forName(session.getConfig(guess[PROPOSAL_ENC_ALGS_CTOS]))
                            .asSubclass(Cipher.class);
            final Cipher _c2scipher = _c2sclazz.getDeclaredConstructor().newInstance();
            _c2sAEAD = _c2scipher.isAEAD();
            if (_c2sAEAD) {
                guess[PROPOSAL_MAC_ALGS_CTOS] = null;
            }
        } catch (final Exception | LinkageError e) {
            throw new JSchException(e.toString(), e);
        }

        if (session.getLogger().isEnabled(Logger.INFO)) {
            session.getLogger().log(Logger.INFO,
                    "kex: algorithm: " + guess[PROPOSAL_KEX_ALGS]);
            session.getLogger().log(Logger.INFO,
                    "kex: host key algorithm: " + guess[PROPOSAL_SERVER_HOST_KEY_ALGS]);
            session.getLogger().log(Logger.INFO,
                    "kex: server->client" +
                            " cipher: " + guess[PROPOSAL_ENC_ALGS_STOC] +
                            " MAC: " + (_s2cAEAD ? ("<implicit>") : (guess[PROPOSAL_MAC_ALGS_STOC])) +
                            " compression: " + guess[PROPOSAL_COMP_ALGS_STOC]);
            session.getLogger().log(Logger.INFO,
                    "kex: client->server" +
                            " cipher: " + guess[PROPOSAL_ENC_ALGS_CTOS] +
                            " MAC: " + (_c2sAEAD ? ("<implicit>") : (guess[PROPOSAL_MAC_ALGS_CTOS])) +
                            " compression: " + guess[PROPOSAL_COMP_ALGS_CTOS]);
        }

        return guess;
    }

    public String getFingerPrint() {
        final HASH hash;
        try {
            final String _c = session.getConfig("FingerprintHash").toLowerCase();
            final Class<? extends HASH> c =
                    Class.forName(session.getConfig(_c)).asSubclass(HASH.class);
            hash = c.getDeclaredConstructor().newInstance();
        } catch (final Exception e) {
            session.getLogger().log(Logger.FATAL,
                    "Unable to load the fingerprint hash class", e);
            throw new JSchErrorException("Unable to load the fingerprint hash class", e);
        }
        try {
            return Util.getFingerPrint(hash, getHostKey(), true, false);
        } catch (final JSchException e) {
            throw new JSchErrorException(e);
        }
    }

    byte[] getK() {
        return K;
    }

    byte[] getH() {
        return H;
    }

    HASH getHash() {
        return sha;
    }

    byte[] getHostKey() {
        return K_S;
    }

    /*
     * It seems JCE included in Oracle's Java7u6(and later) has suddenly changed
     * its behavior.  The secrete generated by KeyAgreement#generateSecret()
     * may start with 0, even if it is a positive value.
     */
    protected byte[] normalize(final byte[] secret) {
        if (secret.length > 1 &&
                secret[0] == 0 && (secret[1] & 0x80) == 0) {
            final byte[] tmp = new byte[secret.length - 1];
            System.arraycopy(secret, 1, tmp, 0, tmp.length);
            return normalize(tmp);
        } else {
            return secret;
        }
    }

    protected boolean verify(final String alg, final byte[] K_S, final int index,
                             final byte[] sig_of_H) throws Exception {
        int i, j;

        i = index;
        boolean result = false;

        switch (alg) {
            case "ssh-rsa": {
                byte[] tmp;
                final byte[] ee;
                final byte[] n;

                type = RSA;
                key_alg_name = alg;

                j = ((K_S[i++] << 24) & 0xff000000) | ((K_S[i++] << 16) & 0x00ff0000) |
                        ((K_S[i++] << 8) & 0x0000ff00) | ((K_S[i++]) & 0x000000ff);
                tmp = new byte[j];
                System.arraycopy(K_S, i, tmp, 0, j);
                i += j;
                ee = tmp;
                j = ((K_S[i++] << 24) & 0xff000000) | ((K_S[i++] << 16) & 0x00ff0000) |
                        ((K_S[i++] << 8) & 0x0000ff00) | ((K_S[i++]) & 0x000000ff);
                tmp = new byte[j];
                System.arraycopy(K_S, i, tmp, 0, j);
                i += j;
                n = tmp;

                final SignatureRSA sig;
                final Buffer buf = new Buffer(sig_of_H);
                final String foo = Util.byte2str(buf.getString());
                try {
                    final Class<? extends SignatureRSA> c =
                            Class.forName(session.getConfig(foo))
                                    .asSubclass(SignatureRSA.class);
                    sig = c.getDeclaredConstructor().newInstance();
                    sig.init();
                } catch (final Exception | LinkageError e) {
                    throw JSchNotImplementedException.forFeature(foo, e);
                }
                sig.setPubKey(ee, n);
                sig.update(H);
                result = sig.verify(sig_of_H);

                if (session.getLogger().isEnabled(Logger.INFO)) {
                    session.getLogger().log(Logger.INFO,
                            "ssh_rsa_verify: " + foo + " signature " + result);
                }
                break;
            }
            case "ssh-dss": {
                byte[] q = null;
                byte[] tmp;
                final byte[] p;
                final byte[] g;
                final byte[] f;

                type = DSS;
                key_alg_name = alg;

                j = ((K_S[i++] << 24) & 0xff000000) | ((K_S[i++] << 16) & 0x00ff0000) |
                        ((K_S[i++] << 8) & 0x0000ff00) | ((K_S[i++]) & 0x000000ff);
                tmp = new byte[j];
                System.arraycopy(K_S, i, tmp, 0, j);
                i += j;
                p = tmp;
                j = ((K_S[i++] << 24) & 0xff000000) | ((K_S[i++] << 16) & 0x00ff0000) |
                        ((K_S[i++] << 8) & 0x0000ff00) | ((K_S[i++]) & 0x000000ff);
                tmp = new byte[j];
                System.arraycopy(K_S, i, tmp, 0, j);
                i += j;
                q = tmp;
                j = ((K_S[i++] << 24) & 0xff000000) | ((K_S[i++] << 16) & 0x00ff0000) |
                        ((K_S[i++] << 8) & 0x0000ff00) | ((K_S[i++]) & 0x000000ff);
                tmp = new byte[j];
                System.arraycopy(K_S, i, tmp, 0, j);
                i += j;
                g = tmp;
                j = ((K_S[i++] << 24) & 0xff000000) | ((K_S[i++] << 16) & 0x00ff0000) |
                        ((K_S[i++] << 8) & 0x0000ff00) | ((K_S[i++]) & 0x000000ff);
                tmp = new byte[j];
                System.arraycopy(K_S, i, tmp, 0, j);
                i += j;
                f = tmp;

                final SignatureDSA sig;
                try {
                    final Class<? extends SignatureDSA> c =
                            Class.forName(session.getConfig("signature.dss"))
                                    .asSubclass(SignatureDSA.class);
                    sig = c.getDeclaredConstructor().newInstance();
                    sig.init();
                } catch (final Exception | LinkageError e) {
                    throw JSchNotImplementedException.forFeature("signature.dss", e);
                }
                sig.setPubKey(f, p, q, g);
                sig.update(H);
                result = sig.verify(sig_of_H);

                if (session.getLogger().isEnabled(Logger.INFO)) {
                    session.getLogger().log(Logger.INFO,
                            "ssh_dss_verify: signature " + result);
                }
                break;
            }
            case "ecdsa-sha2-nistp256":
            case "ecdsa-sha2-nistp384":
            case "ecdsa-sha2-nistp521": {
                byte[] tmp;
                final byte[] r;
                final byte[] s;

                // RFC 5656,
                type = ECDSA;
                key_alg_name = alg;

                j = ((K_S[i++] << 24) & 0xff000000) | ((K_S[i++] << 16) & 0x00ff0000) |
                        ((K_S[i++] << 8) & 0x0000ff00) | ((K_S[i++]) & 0x000000ff);
                tmp = new byte[j];
                System.arraycopy(K_S, i, tmp, 0, j);
                i += j;
                j = ((K_S[i++] << 24) & 0xff000000) | ((K_S[i++] << 16) & 0x00ff0000) |
                        ((K_S[i++] << 8) & 0x0000ff00) | ((K_S[i++]) & 0x000000ff);
                i++;
                tmp = new byte[(j - 1) / 2];
                System.arraycopy(K_S, i, tmp, 0, tmp.length);
                i += (j - 1) / 2;
                r = tmp;
                tmp = new byte[(j - 1) / 2];
                System.arraycopy(K_S, i, tmp, 0, tmp.length);
                i += (j - 1) / 2;
                s = tmp;

                final SignatureECDSA sig;
                try {
                    final Class<? extends SignatureECDSA> c =
                            Class.forName(session.getConfig(alg))
                                    .asSubclass(SignatureECDSA.class);
                    sig = c.getDeclaredConstructor().newInstance();
                    sig.init();
                } catch (final Exception | LinkageError e) {
                    throw JSchNotImplementedException.forFeature(alg, e);
                }
                sig.setPubKey(r, s);
                sig.update(H);
                result = sig.verify(sig_of_H);

                if (session.getLogger().isEnabled(Logger.INFO)) {
                    session.getLogger().log(Logger.INFO,
                            "ssh_ecdsa_verify: " + alg + " signature " + result);
                }
                break;
            }
            case "ssh-ed25519":
            case "ssh-ed448": {
                // RFC 8709,
                type = EDDSA;
                key_alg_name = alg;

                j = ((K_S[i++] << 24) & 0xff000000) | ((K_S[i++] << 16) & 0x00ff0000) |
                        ((K_S[i++] << 8) & 0x0000ff00) | ((K_S[i++]) & 0x000000ff);
                final byte[] tmp = new byte[j];
                System.arraycopy(K_S, i, tmp, 0, j);
                i += j;

                final SignatureEdDSA sig;
                try {
                    final Class<? extends SignatureEdDSA> c =
                            Class.forName(session.getConfig(alg))
                                    .asSubclass(SignatureEdDSA.class);
                    sig = c.getDeclaredConstructor().newInstance();
                    sig.init();
                } catch (final Exception | LinkageError e) {
                    throw JSchNotImplementedException.forFeature(alg, e);
                }

                sig.setPubKey(tmp);

                sig.update(H);

                result = sig.verify(sig_of_H);

                if (session.getLogger().isEnabled(Logger.INFO)) {
                    session.getLogger().log(Logger.INFO,
                            "ssh_eddsa_verify: " + alg + " signature " + result);
                }
                break;
            }
            default:
                session.getLogger().log(Logger.ERROR, "Unknown algorithm");
                break;
        }

        return result;
    }

}

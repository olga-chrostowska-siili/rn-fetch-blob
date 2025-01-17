package com.RNFetchBlob;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;


public class RNFetchBlobUtils {

    public static String getMD5(String input) {
        String result = null;

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());
            byte[] digest = md.digest();

            StringBuilder sb = new StringBuilder();

            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }

            result = sb.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            // TODO: Is discarding errors the intent? (https://www.owasp.org/index.php/Return_Inside_Finally_Block)
            return result;
        }

    }

    public static void emitWarningEvent(String data) {
        WritableMap args = Arguments.createMap();
        args.putString("event", "warn");
        args.putString("detail", data);

        // emit event to js context
        RNFetchBlob.RCTContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(RNFetchBlobConst.EVENT_MESSAGE, args);
    }

    public static OkHttpClient.Builder getUnsafeOkHttpClient(OkHttpClient client, final String trustyCn) {
        try {
            // Create a trust manager that does not validate certificate chains
            final X509TrustManager x509TrustManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            };
            final TrustManager[] trustAllCerts = new TrustManager[]{ x509TrustManager };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = client.newBuilder();
            builder.sslSocketFactory(sslSocketFactory, x509TrustManager);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    try {
                        Certificate[] certificates = session.getPeerCertificates();
                        if (certificates.length == 0) {
                            Log.d("RNFetch", "hostnameVerify return false, as there ar no certificates in the chain");
                            return false;
                        }

                        Certificate cert = certificates[0];
                        if (cert instanceof X509Certificate) { 
                            X509Certificate x509Cert = ( X509Certificate) cert;
                            String issuerDistinguishedName = x509Cert.getIssuerX500Principal().getName();
                            String cnRegexPattern = "CN=([^,]*)";

                            Pattern cmPattern = Pattern.compile(cnRegexPattern);
                            Matcher cmMatcher = cmPattern.matcher(issuerDistinguishedName);
                    
                            String commonName = "";
                            if (cmMatcher.lookingAt()) {
                                commonName = cmMatcher.group(1);
                                System.out.println(commonName);
                            }

                            if (!commonName.equals(trustyCn)) {
                                Log.d("RNFetch", "hostnameVerify return false, as commonName does not match");
                                return false;
                            }

                            Date notValidBefore = x509Cert.getNotBefore();
                            Date notValidAfter = x509Cert.getNotAfter();
                            Date now = new Date();

                            if (notValidBefore.after(now) || notValidAfter.before(now)) {
                                Log.d("RNFetch", "hostnameVerify return false, as certificate has expired");
                                return false;
                            }
                        } else {
                            return true;
                        }
                    } catch (Exception e) {
                        Log.d("RNFetch", "error obatining certificates, return false");
                        return false;
                    }
                    return true;
                }
            });

            return builder;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

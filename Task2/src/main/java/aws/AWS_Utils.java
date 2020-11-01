package aws;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AWS_Utils {
    public static void createSecurityGroup(String securityGroupName, AmazonEC2Client amazonEC2Client, Logger logger) {
        try {
            CreateSecurityGroupRequest createSecurityGroupRequest = new CreateSecurityGroupRequest();
            createSecurityGroupRequest.withGroupName(securityGroupName).withDescription("My security group");

            amazonEC2Client.createSecurityGroup(createSecurityGroupRequest);
        } catch (AmazonEC2Exception ex) {
            logger.warning("SecurityGroup already exists. Skipping...");
        }

    }

    public static void createIpPermission(String securityGroupName, AmazonEC2Client amazonEC2Client, Logger logger) {
        IpPermission ipPermission = new IpPermission();

        try {
            IpRange ipRange1 = new IpRange().withCidrIp("0.0.0.0/0");

            ipPermission.withIpv4Ranges(Collections.singletonList(ipRange1))
                    .withIpProtocol("tcp")
                    .withFromPort(22)
                    .withToPort(22);

            AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest =
                    new AuthorizeSecurityGroupIngressRequest();

            authorizeSecurityGroupIngressRequest.withGroupName(securityGroupName)
                    .withIpPermissions(ipPermission);

            amazonEC2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);
        } catch (AmazonEC2Exception ex) {
            logger.warning("Rule already exists. Skipped...");
        }
    }

    public static void generateKeyPair(String keyName, AmazonEC2Client amazonEC2Client, Logger logger) {
        try {
            CreateKeyPairRequest createKeyPairRequest = new CreateKeyPairRequest();
            createKeyPairRequest.withKeyName(keyName);
            amazonEC2Client.createKeyPair(createKeyPairRequest);
        } catch (AmazonEC2Exception ex) {
            logger.warning("Key pair already exists. Skipped...");
        }
    }

    public static void sendSSHCommandToInstance(String instanceIp, String keyFingerprint, String command,
                                                Logger logger, int counter) throws Exception {

        Session session = null;
        ChannelExec channelExec = null;

        try {
            JSch jsch = new JSch();
            JSch.setConfig("StrictHostKeyChecking", "no");
            jsch.addIdentity(keyFingerprint);
            session = jsch.getSession("ec2-user", instanceIp, 22);
            session.connect();

            channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);
            ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
            channelExec.setOutputStream(responseStream);
            channelExec.connect();


            while (channelExec.isConnected())
                Thread.sleep(100);

            if (channelExec.isClosed()) {
                logger.info("Channel exit status: " + channelExec.getExitStatus());
                if (channelExec.getExitStatus() != 0 && counter < 10) {
                    sendSSHCommandToInstance(instanceIp, keyFingerprint, command, logger, ++counter);
                } else if (channelExec.getExitStatus() != 0) {
                    throw new SendCommandException("Could not send Command to instance after 10 retries");
                }
            } else {
                channelExec.sendSignal("2");
            }

            String responseString = new String(responseStream.toByteArray());
            logger.info(responseString);
        } finally {
            if (session != null)
                session.disconnect();
            if (channelExec != null)
                channelExec.disconnect();
        }
    }

    public static void sendFileToInstance(String instanceIp, String keyFingerprint, String command, String path) throws Exception {

        Session session = null;
        ChannelExec channelExec = null;
        FileInputStream fileInputStream = null;

        try {
            JSch jsch = new JSch();
            JSch.setConfig("StrictHostKeyChecking", "no");
            jsch.addIdentity(keyFingerprint);
            session = jsch.getSession("ec2-user", instanceIp, 22);
            session.connect();

            channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);

            InputStream in = channelExec.getInputStream();
            OutputStream out = channelExec.getOutputStream();

            channelExec.connect();

            if (checkAck(in) != 0) {
                System.exit(0);
            }

            File file = new File(path);

            long filesize = file.length();
            command = "C0644 " + filesize + " ";
            if (path.lastIndexOf('/') > 0) {
                command += path.substring(path.lastIndexOf('/') + 1);
            } else {
                command += path;
            }
            command += "\n";

            out.write(command.getBytes());
            out.flush();

            if (checkAck(in) != 0) {
                System.exit(0);
            }

            fileInputStream = new FileInputStream(path);
            byte[] buf = new byte[1024];
            while (true) {
                int len = fileInputStream.read(buf, 0, buf.length);
                if (len <= 0)
                    break;
                out.write(buf, 0, len);
            }

            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

            if (checkAck(in) != 0) {
                System.exit(0);
            }

            out.close();
        } finally {
            if (session != null)
                session.disconnect();
            if (channelExec != null)
                channelExec.disconnect();
            if (fileInputStream != null)
                fileInputStream.close();
        }
    }

    public static void getFileFromInstance(String instanceIp, String keyFingerprint, String command, String path) throws Exception {

        Session session = null;
        ChannelExec channelExec = null;
        FileOutputStream fileOutputStream = null;

        try {
            JSch jsch = new JSch();
            JSch.setConfig("StrictHostKeyChecking", "no");
            jsch.addIdentity(keyFingerprint);
            session = jsch.getSession("ec2-user", instanceIp, 22);
            session.connect();

            channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);

            // get I/O streams for remote scp
            OutputStream out = channelExec.getOutputStream();
            InputStream in = channelExec.getInputStream();

            channelExec.connect();

            byte[] buf = new byte[1024];

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

            // read '0644 '
            in.read(buf, 0, 6);

            long filesize = 0L;
            // filesize must be positive and after filesize is a whitespace
            while (in.read(buf, 0, 1) >= 0 && buf[0] != ' ') {
                // read filesize byte by byte and subtract ascii value of 0 to get correct numbers, multiply by 10
                // for every decimal position
                filesize = filesize * 10L + (long) (buf[0] - '0');
            }

            String fileName = null;
            for (int i = 0; ; i++) {
                in.read(buf, i, 1);
                // 0x0a is hexadecimal for \n
                if (buf[i] == (byte) 0x0a) {
                    fileName = new String(buf, 0, i);
                    break;
                }
            }

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

            fileOutputStream = new FileOutputStream(path);
            // if buf is to small for the whole file we get chunks with maxSize
            int maxSize;
            while (true) {
                if (buf.length < filesize)
                    maxSize = buf.length;
                else
                    maxSize = (int) filesize;
                maxSize = in.read(buf, 0, maxSize);
                if (maxSize < 0) {
                    // error
                    break;
                }
                fileOutputStream.write(buf, 0, maxSize);
                filesize -= maxSize;
                // After reading the whole file we break out of the loop
                if (filesize == 0L)
                    break;
            }

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

        } finally {
            if (session != null)
                session.disconnect();
            if (channelExec != null)
                channelExec.disconnect();
            if (fileOutputStream != null)
                fileOutputStream.close();
        }
    }

    private static int checkAck(InputStream in) throws IOException {
        int b = in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //         -1
        if (b == 0) return b;
        if (b == -1) return b;

        if (b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
                c = in.read();
                sb.append((char) c);
            }
            while (c != '\n');
            if (b == 1) { // error
                System.out.print(sb.toString());
            }
            if (b == 2) { // fatal error
                System.out.print(sb.toString());
            }
        }
        return b;
    }
}
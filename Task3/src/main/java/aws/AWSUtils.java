package aws;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.jcraft.jsch.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class AWSUtils {
    static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    //using PosixFilePermission to set file permissions 777
    static final Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>(Collections.singletonList(PosixFilePermission.OWNER_READ));

    public static void createSecurityGroup(String securityGroupName, AmazonEC2Client amazonEC2Client) {
        try {
            CreateSecurityGroupRequest createSecurityGroupRequest = new CreateSecurityGroupRequest();
            createSecurityGroupRequest.withGroupName(securityGroupName).withDescription("My security group");

            amazonEC2Client.createSecurityGroup(createSecurityGroupRequest);
        } catch (AmazonEC2Exception ex) {
            logger.warning("SecurityGroup already exists. Skipping...");
        }

    }

    public static void createIpPermission(String securityGroupName, AmazonEC2Client amazonEC2Client) {
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

    public static void generateKeyPair(String keyName, AmazonEC2Client amazonEC2Client) {
        try {
            CreateKeyPairRequest createKeyPairRequest = new CreateKeyPairRequest();
            createKeyPairRequest.withKeyName(keyName);
            savePrivateKey(amazonEC2Client.createKeyPair(createKeyPairRequest));
        } catch (AmazonEC2Exception ex) {
            logger.warning("Key pair already exists. Skipped...");
        }
    }

    private static void savePrivateKey(CreateKeyPairResult createKeyPairResult) {
        try {
            File privateKeyFile = new File(createKeyPairResult.getKeyPair().getKeyName() + ".pem");
            if (privateKeyFile.createNewFile()) {
                writeToFile(privateKeyFile, createKeyPairResult.getKeyPair().getKeyMaterial());
                Files.setPosixFilePermissions(privateKeyFile.toPath(), perms);
                logger.info("Private key file created: " + privateKeyFile.getName());
            } else {
                logger.info("File already exists.");
            }
        } catch (IOException e) {
            logger.severe("There was an error while creating the private key file!");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static void writeToFile(File fileToWrite, String stringToWrite) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(fileToWrite);
        DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(fileOutputStream));
        dataOutputStream.writeUTF(stringToWrite);
        dataOutputStream.close();

        String verification;
        FileInputStream fileInputStream = new FileInputStream(fileToWrite);
        DataInputStream dataInputStream = new DataInputStream(fileInputStream);
        verification = dataInputStream.readUTF();
        dataInputStream.close();
        if(!verification.equals(stringToWrite)) {
            logger.severe("Failed to write key to file!");
            System.exit(-1);
        }
    }

    public static void sendSSHCommandToInstance(String instanceIp, String keyFingerprint, String command,
                                                int counter) throws Exception {

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
                    sendSSHCommandToInstance(instanceIp, keyFingerprint, command, ++counter);
                } else if (channelExec.getExitStatus() != 0) {
                    throw new SendCommandException("Could not send Command to instance after 10 retries");
                }
            } else {
                channelExec.sendSignal("2");
            }
        } finally {
            if (session != null)
                session.disconnect();
            if (channelExec != null)
                channelExec.disconnect();
        }
    }

    public static void sendFileToInstance(String instanceIp, String keyPath, String path) {
        Session session = null;
        ChannelSftp channelSftp = null;
        FileInputStream fileInputStream = null;
        File inputFile = null;

        try {
            JSch jsch = new JSch();
            JSch.setConfig("StrictHostKeyChecking", "no");
            jsch.addIdentity(keyPath);
            session = jsch.getSession("ec2-user", instanceIp, 22);
            session.connect();

            channelSftp = (ChannelSftp) session.openChannel("sftp");

            logger.info(path);

            inputFile = new File(path);
            fileInputStream = new FileInputStream(inputFile);

            channelSftp.connect();

            channelSftp.put(fileInputStream, path);
        } catch (NullPointerException e) {
            logger.severe("Error!");
            e.printStackTrace();
            System.exit(-1);
        } catch (FileNotFoundException e) {
            logger.severe("Couldn't find file!");
            e.printStackTrace();
            System.exit(-1);
        } catch (SftpException e) {
            logger.severe("SFTP upload unsuccessful!");
            e.printStackTrace();
            System.exit(-1);
        } catch (JSchException e) {
            logger.severe("SSH connection failed!");
            e.printStackTrace();
            System.exit(-1);
        } finally {
            if(channelSftp != null)
                channelSftp.disconnect();
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

            // read 'C0644 '
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

            if (in.read() != 0) {
                logger.severe("File download failed!");
                System.exit(-1);
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
}
package aws;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
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

            while (channelExec.isConnected()) {
                Thread.sleep(100);
            }

            if (channelExec.isClosed()) {
                logger.info("Channel exit status: " + channelExec.getExitStatus());
                if (channelExec.getExitStatus() != 0 && counter < 10) {
                    sendSSHCommandToInstance(instanceIp, keyFingerprint, command, logger, ++counter);
                } else if (channelExec.getExitStatus() != 0) {
                    throw new SendCommandException("Could not send Command to instance after 10 retries");
                }
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
}
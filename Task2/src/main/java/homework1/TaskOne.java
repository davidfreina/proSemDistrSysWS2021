package homework1;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.waiters.WaiterParameters;
import com.amazonaws.waiters.WaiterTimedOutException;
import com.jcraft.jsch.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Fill in your code into the main method of this class.
 * 
 * 
 * @author Mathias Thoeni
 * @author David Freina
 *
 */
public class TaskOne {

	public static void main(String[] args) {

		final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		final String securityGroupName = "ThoeniFreinaAWS";
		final String keyName = "TaskTwoKey";

		ProfileCredentialsProvider profileCredentialsProvider = new ProfileCredentialsProvider();
		if(profileCredentialsProvider.getCredentials() == null) {
			logger.severe("Could not get credentials!");
			System.exit(-1);
		}

		AmazonEC2Client amazonEC2Client = (AmazonEC2Client) AmazonEC2Client.builder().build();

		createSecurityGroup(securityGroupName, amazonEC2Client, logger);

		createIpPermission(securityGroupName, amazonEC2Client, logger);

		String keyFingerprint = generateKeyPair(keyName, amazonEC2Client, logger);

		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

		runInstancesRequest.withImageId("ami-0947d2ba12ee1ff75")
				.withInstanceType(InstanceType.T2Micro)
				.withMinCount(1)
				.withMaxCount(1)
				.withKeyName(keyName)
				.withSecurityGroups(securityGroupName);

		long start = System.currentTimeMillis();
		long end = 1;

		RunInstancesResult result = amazonEC2Client.runInstances(runInstancesRequest);

		List<String> instanceIds = result.getReservation()
				.getInstances()
				.stream()
				.map(Instance::getInstanceId)
				.collect(Collectors.toList());

		try {
			amazonEC2Client.waiters().instanceRunning().run(new WaiterParameters<>(new DescribeInstancesRequest().withInstanceIds(instanceIds)));
		} catch (WaiterTimedOutException waiterTimedOutException) {
			logger.severe("Could not detect all instances as running! Time measurements will be wrong!");
		}

		List<Address> addressList = amazonEC2Client.describeAddresses().getAddresses();

		end = System.currentTimeMillis();

		logger.info("Time elapsed: " + (end-start));

		TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest(instanceIds);
		amazonEC2Client.terminateInstances(terminateInstancesRequest);

		try {
			amazonEC2Client.waiters().instanceTerminated().run(new WaiterParameters<>(new DescribeInstancesRequest().withInstanceIds(instanceIds)));
			logger.info("Terminated all instances. Exiting...");
		} catch (WaiterTimedOutException waiterTimedOutException) {
			logger.severe("Could not terminate all instances!");
			System.exit(-1);
		}
	}

	private static void createSecurityGroup(String securityGroupName, AmazonEC2Client amazonEC2Client, Logger logger) {
		try {
			CreateSecurityGroupRequest createSecurityGroupRequest = new CreateSecurityGroupRequest();
			createSecurityGroupRequest.withGroupName(securityGroupName).withDescription("My security group");

			amazonEC2Client.createSecurityGroup(createSecurityGroupRequest);
		}
		catch (AmazonEC2Exception ex){
			logger.warning("SecurityGroup already exists. Skipping...");
		}

	}

	private static void createIpPermission(String securityGroupName, AmazonEC2Client amazonEC2Client, Logger logger) {
		IpPermission ipPermission = new IpPermission();

		try {
			IpRange ipRange1 = new IpRange().withCidrIp("111.111.111.111/32");
			IpRange ipRange2 = new IpRange().withCidrIp("150.150.150.150/32");

			ipPermission.withIpv4Ranges(Arrays.asList(ipRange1, ipRange2))
					.withIpProtocol("tcp")
					.withFromPort(22)
					.withToPort(22);

			AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest =
					new AuthorizeSecurityGroupIngressRequest();

			authorizeSecurityGroupIngressRequest.withGroupName(securityGroupName)
					.withIpPermissions(ipPermission);

			amazonEC2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);
		}
		catch(AmazonEC2Exception ex){
			logger.warning("Rule already exists. Skipped...");
		}
	}

	private static String generateKeyPair(String keyName, AmazonEC2Client amazonEC2Client, Logger logger) {
		DescribeKeyPairsRequest describeKeyPairsRequest = new DescribeKeyPairsRequest();
		DescribeKeyPairsResult describeKeyPairsResult = amazonEC2Client.describeKeyPairs(describeKeyPairsRequest);

		List<KeyPairInfo> keyPairList = describeKeyPairsResult.getKeyPairs().stream().filter(keyPairInfo -> keyPairInfo.getKeyName().equals(keyName)).collect(Collectors.toList());

		if (keyPairList.isEmpty()) {
			CreateKeyPairRequest createKeyPairRequest = new CreateKeyPairRequest();
			createKeyPairRequest.withKeyName(keyName);

			return amazonEC2Client.createKeyPair(createKeyPairRequest).getKeyPair().getKeyFingerprint();
		} else {
			return keyPairList.get(0).getKeyFingerprint();
		}
	}

	private static void sendSSHCommandToInstance(String instanceIp, String keyFingerprint, String command, Logger logger, int counter) throws Exception {

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

			while(channelExec.isConnected()) {
				Thread.sleep(100);
			}

			if (channelExec.isClosed()) {
				logger.info("Channel exit status: " + channelExec.getExitStatus());
				if (channelExec.getExitStatus() != 0 && counter < 10) {
					sendSSHCommandToInstance(instanceIp, keyFingerprint, command, logger, ++counter);
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

package homework1;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Fill in your code into the main method of this class.
 * 
 * 
 * @author Mathias Thöni & David Freina
 *
 */
public class TaskOne {

	public static void main(String[] args) {

		final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		final String securityGroupName = "MathiasThoeniAWS";
		final String keyName = "testKey";

		AWSCredentials credentials;

		ProfileCredentialsProvider profileCredentialsProvider = new ProfileCredentialsProvider();
		credentials = profileCredentialsProvider.getCredentials();

		AmazonEC2Client amazonEC2Client = (AmazonEC2Client) AmazonEC2Client.builder().build();

		try {
			CreateSecurityGroupRequest csgr = new CreateSecurityGroupRequest();
			csgr.withGroupName(securityGroupName).withDescription("My security group");

			CreateSecurityGroupResult createSecurityGroupResult = amazonEC2Client.createSecurityGroup(csgr);
		}
		catch (AmazonEC2Exception ex){
			logger.warning("SecurityGroup already exists. Skipping...");
		}

		IpPermission ipPermission = new IpPermission();

		try {
			IpRange ipRange1 = new IpRange().withCidrIp("111.111.111.111/32");
			IpRange ipRange2 = new IpRange().withCidrIp("150.150.150.150/32");

			ipPermission.withIpv4Ranges(Arrays.asList(new IpRange[] {ipRange1, ipRange2}))
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

//		DeleteKeyPairRequest deleteKeyPairRequest = new DeleteKeyPairRequest();
//		deleteKeyPairRequest.withKeyName(keyName);
//
//		DeleteKeyPairResult deleteKeyPairResult = amazonEC2Client.deleteKeyPair(deleteKeyPairRequest);

		try {
			CreateKeyPairRequest createKeyPairRequest = new CreateKeyPairRequest();
			createKeyPairRequest.withKeyName(keyName);

			CreateKeyPairResult createKeyPairResult = amazonEC2Client.createKeyPair(createKeyPairRequest);

			KeyPair keyPair;

			keyPair = createKeyPairResult.getKeyPair();

			String privateKey = keyPair.getKeyMaterial();
		}
		catch(AmazonEC2Exception ex){
			logger.warning("KeyPair already exists. Skipped...");
		}

		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

		runInstancesRequest.withImageId("ami-0947d2ba12ee1ff75")
				.withInstanceType(InstanceType.T2Micro)
				.withMinCount(1)
				.withMaxCount(1)
				.withKeyName(keyName)
				.withSecurityGroups(securityGroupName);

		long start = System.currentTimeMillis();

		RunInstancesResult result = amazonEC2Client.runInstances(runInstancesRequest);

		List<String> instanceIds = result.getReservation()
				.getInstances()
				.stream()
				.map(Instance::getInstanceId)
				.collect(Collectors.toList());

		DescribeInstanceStatusRequest statusRequest = new DescribeInstanceStatusRequest().withInstanceIds(instanceIds);

		DescribeInstanceStatusResult describeInstanceStatusResult = amazonEC2Client.describeInstanceStatus(statusRequest);

		if(describeInstanceStatusResult.getInstanceStatuses().get(0).getInstanceState().getName().equals(InstanceStateName.Running.toString())){
			
		}

		long end = System.currentTimeMillis();

		logger.info("Time elapsed: " + (end-start));

		TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest(instanceIds);
		TerminateInstancesResult terminateInstancesResult = amazonEC2Client.terminateInstances(terminateInstancesRequest);

		terminateInstancesResult.getTerminatingInstances().forEach(instanceStateChange -> {
			if (!instanceStateChange.getCurrentState().getName().equals(InstanceStateName.ShuttingDown.toString())){
				logger.severe("An error occurred while terminating instances!");
			}
		});

		logger.info("Exiting...");
	}
}

package homework1;

import aws.AWS_Utils;
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

		AWS_Utils.createSecurityGroup(securityGroupName, amazonEC2Client, logger);

		AWS_Utils.createIpPermission(securityGroupName, amazonEC2Client, logger);

		String keyFingerprint = AWS_Utils.generateKeyPair(keyName, amazonEC2Client, logger);

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


}

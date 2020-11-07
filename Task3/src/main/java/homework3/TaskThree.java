package homework3;

import aws.AWSUtils;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.waiters.WaiterParameters;
import com.amazonaws.waiters.WaiterTimedOutException;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Fill in your code into the main method of this class.
 * 
 * 
 * @author Fedor Smirnov
 *
 */
public class TaskThree {
	static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	static final String SECURITY_GROUP_NAME = "ThoeniFreinaAWSAllowSSH";
	static final String KEY_NAME = "thoeni-freina-key-task3";
	static final String KEY_PATH = KEY_NAME + ".pem";
	static final String CALC_FIB_FILE = "calc_fib.jar";
	static final String DATA_FILE = "input_full.csv";
	static final String OUTPUT_FILE = "output.csv";
	static final String DOCKER_IMAGE_NAME = "mathiasthoeni/calc_fib:task3v2";

	public static void main(String[] args) {

		ProfileCredentialsProvider profileCredentialsProvider = new ProfileCredentialsProvider();
		if (profileCredentialsProvider.getCredentials() == null) {
			logger.severe("Could not get credentials!");
			System.exit(-1);
		}

		AmazonEC2Client amazonEC2Client = (AmazonEC2Client) AmazonEC2Client.builder().build();

		AWSUtils.createSecurityGroup(SECURITY_GROUP_NAME, amazonEC2Client);

		AWSUtils.createIpPermission(SECURITY_GROUP_NAME, amazonEC2Client);

		AWSUtils.generateKeyPair(KEY_NAME, amazonEC2Client);

		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

		runInstancesRequest.withImageId("ami-0947d2ba12ee1ff75")
				.withInstanceType(InstanceType.T2Micro)
				.withMinCount(1)
				.withMaxCount(1)
				.withKeyName(KEY_NAME)
				.withSecurityGroups(SECURITY_GROUP_NAME);

		RunInstancesResult result = amazonEC2Client.runInstances(runInstancesRequest);

		List<Instance> instances = result.getReservation().getInstances();

		List<String> instanceIds = instances.stream()
				.map(Instance::getInstanceId)
				.collect(Collectors.toList());

		logger.info("Waiting for all instances to be running...");
		try {
			amazonEC2Client.waiters().instanceRunning().run(new WaiterParameters<>(new DescribeInstancesRequest().withInstanceIds(instanceIds)));
		} catch (WaiterTimedOutException waiterTimedOutException) {
			logger.severe("Could not detect all instances are running!");
		}

		// For some reason we had to wait for the instances to have status ok because otherwise we got a 'Connection refused' when connecting with ssh
		logger.info("Waiting for instance status to be ok...");
		try {
			amazonEC2Client.waiters().instanceStatusOk().run(new WaiterParameters<>(new DescribeInstanceStatusRequest().withInstanceIds(instanceIds)));
		} catch (WaiterTimedOutException waiterTimedOutException) {
			logger.severe("Could not detect all instances as ok!");
		}

		logger.info("Getting Instance IPs...");
		DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest().withInstanceIds(instanceIds);
		List<String> instanceIps =
				amazonEC2Client.describeInstances(describeInstancesRequest).getReservations().stream().map(Reservation::getInstances).flatMap(List::stream).map(Instance::getPublicIpAddress).collect(Collectors.toList());

		//instanceIps.forEach(instanceIp -> new Thread(() -> logger.info("Full took: " + calculateFib(instanceIp, inputFullFile, outputFullFile) + "ms")));
		logger.info("Full took: " + calculateFib(instanceIps.get(0), DATA_FILE, OUTPUT_FILE) + "ms");

		logger.info("Waiting for instances to change state to terminated...");
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

	private static long calculateFib(String ipAddress, String inputFile, String outputFile) {

		long start = System.currentTimeMillis();

		final String[] commands = {"sudo amazon-linux-extras install docker", "sudo service docker start", "sudo docker pull " + DOCKER_IMAGE_NAME, "sudo docker run -v /home/ec2-user:/workdir --rm " + DOCKER_IMAGE_NAME};

		try {
			logger.info("Sending calc_fib.jar to instance with IP: " + ipAddress);
			AWSUtils.sendFileToInstance(ipAddress, KEY_PATH, CALC_FIB_FILE);
			logger.info("Sending " + inputFile + " to instance with IP: " + ipAddress);
			AWSUtils.sendFileToInstance(ipAddress, KEY_PATH, inputFile);
		} catch (Exception e) {
			logger.severe("Could not send file to instance with IP: " + ipAddress);
			logger.severe(e.toString());
			System.exit(-1);
		}
		for(String command : commands) {
			try {
				logger.info("Sending command " + command + " to instance with IP: " + ipAddress);
				AWSUtils.sendSSHCommandToInstance(ipAddress, KEY_PATH, command, 10);
			} catch (Exception e) {
				logger.severe("Could not send command to instance with IP: " + ipAddress);
				logger.severe(e.toString());
				System.exit(-1);
			}
		}
		try {
			logger.info("Getting " + outputFile + " from instance with IP: " + ipAddress);
			AWSUtils.getFileFromInstance(ipAddress, KEY_PATH, outputFile);
		} catch (Exception e) {
			logger.severe("Could not get file from instance with IP: " + ipAddress);
			logger.severe(e.toString());
			System.exit(-1);
		}

		long end = System.currentTimeMillis();

		return end-start;
	}

}

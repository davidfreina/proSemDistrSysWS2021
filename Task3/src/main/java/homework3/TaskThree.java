package homework3;

import aws.AWSUtils;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.waiters.WaiterTimedOutException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Fill in your code into the main method of this class.
 *
 * @author David Freina
 * @author Mathias Thoeni
 *
 */
public class TaskThree {
	static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	static final String SECURITY_GROUP_NAME = "ThoeniFreinaAWSAllowSSH";
	static final String KEY_NAME = "thoeni-freina-key-task3";
	static final String KEY_PATH = KEY_NAME + ".pem";
	static final String DATA_FILE = "input_full.csv";
	static final String OUTPUT_FILE = "output.csv";
	static final String DOCKER_IMAGE_NAME = "davidfreina/calc_fib:v1";
	static final String[] COMMANDS = {"sudo amazon-linux-extras install docker", "sudo service docker start", "sudo docker pull " + DOCKER_IMAGE_NAME, "sudo docker run -v /home/ec2-user:/mount --rm " + DOCKER_IMAGE_NAME};


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

		RunInstancesResult result = AWSUtils.runInstances(amazonEC2Client, 5, 5, KEY_NAME, SECURITY_GROUP_NAME);

		List<Instance> instances = result.getReservation().getInstances();

		List<String> instanceIds = instances.stream()
				.map(Instance::getInstanceId)
				.collect(Collectors.toList());

		logger.info("Waiting for instance status to be running...");
		try {
			AWSUtils.waitForInstanceRunning(amazonEC2Client, instanceIds);
		} catch (WaiterTimedOutException waiterTimedOutException) {
			logger.severe("Could not detect all instances as running!");
		}

		// For some reason we had to wait for the instances to have status ok because otherwise we got a 'Connection refused' when connecting with ssh
		logger.info("Waiting for instance status to be ok...");
		try {
			AWSUtils.waitForInstanceStatusOk(amazonEC2Client, instanceIds);
		} catch (WaiterTimedOutException waiterTimedOutException) {
			logger.severe("Could not detect all instances as ok!");
		}

		logger.info("Getting Instance IPs...");

		List<String> instanceIps = AWSUtils.getInstanceIps(amazonEC2Client, instanceIds);

		List<Thread> threadList = new ArrayList<>();
		List<Long> times = new ArrayList<>();
		instanceIps.forEach(instanceIp -> {
			Thread thread = new Thread(() -> times.add(calculateFib(instanceIp, DATA_FILE, OUTPUT_FILE, COMMANDS)));
			threadList.add(thread);
		});

		threadList.forEach(Thread::start);

		threadList.forEach(thread -> {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});

		logger.info("Statistics: " + times.stream().mapToLong((x) -> x).summaryStatistics());

		logger.info("Waiting for instances to change state to terminated...");
		AWSUtils.terminateInstances(amazonEC2Client, instanceIds);
	}

	private static long calculateFib(String ipAddress, String inputFile, String outputFile, String[] commands) {

		long start = System.currentTimeMillis();

		try {
			logger.info("Sending " + inputFile + " to instance with IP: " + ipAddress);
			AWSUtils.sendFileToInstance(ipAddress, KEY_PATH, inputFile, "input.csv");
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

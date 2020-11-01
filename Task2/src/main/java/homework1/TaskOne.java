package homework1;

import aws.AWS_Utils;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.waiters.WaiterParameters;
import com.amazonaws.waiters.WaiterTimedOutException;
import org.apache.commons.logging.Log;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Fill in your code into the main method of this class.
 *
 * @author Mathias Thoeni
 * @author David Freina
 */
public class TaskOne {

    public static void main(String[] args) throws InterruptedException {

        final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        final String securityGroupName = "ThoeniFreinaAWSAllowSSH";
        final String keyName = "thoeni-freina-key";
        final String keyPath = "~/.ssh/thoeni-freina-key.pem";

        final String calcFibFile = "/home/davidfreina/IdeaProjects/proSemDistrSysWS2021/Task2/calc_fib.jar";

        final String inputFullFile = "/home/davidfreina/IdeaProjects/proSemDistrSysWS2021/Task2/input_full.csv";
        final String inputHalfOneFile = "/home/davidfreina/IdeaProjects/proSemDistrSysWS2021/Task2/input_half_one.csv";
        final String inputHalfTwoFile = "/home/davidfreina/IdeaProjects/proSemDistrSysWS2021/Task2/input_half_two.csv";

        final String outputFullFile = "/home/davidfreina/IdeaProjects/proSemDistrSysWS2021/Task2/output_full.csv";
        final String outputHalfOneFile = "/home/davidfreina/IdeaProjects/proSemDistrSysWS2021/Task2/output_half_one.csv";
        final String outputHalfTwoFile = "/home/davidfreina/IdeaProjects/proSemDistrSysWS2021/Task2/output_half_two.csv";

        ProfileCredentialsProvider profileCredentialsProvider = new ProfileCredentialsProvider();
        if (profileCredentialsProvider.getCredentials() == null) {
            logger.severe("Could not get credentials!");
            System.exit(-1);
        }

        AmazonEC2Client amazonEC2Client = (AmazonEC2Client) AmazonEC2Client.builder().build();

        AWS_Utils.createSecurityGroup(securityGroupName, amazonEC2Client, logger);

        AWS_Utils.createIpPermission(securityGroupName, amazonEC2Client, logger);

        AWS_Utils.generateKeyPair(keyName, amazonEC2Client, logger);

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        runInstancesRequest.withImageId("ami-0947d2ba12ee1ff75")
                .withInstanceType(InstanceType.T2Micro)
                .withMinCount(3)
                .withMaxCount(3)
                .withKeyName(keyName)
                .withSecurityGroups(securityGroupName);

        RunInstancesResult result = amazonEC2Client.runInstances(runInstancesRequest);

        List<Instance> instances = result.getReservation().getInstances();

        List<String> instanceIds = instances.stream()
                .map(Instance::getInstanceId)
                .collect(Collectors.toList());

        try {
            amazonEC2Client.waiters().instanceRunning().run(new WaiterParameters<>(new DescribeInstancesRequest().withInstanceIds(instanceIds)));
        } catch (WaiterTimedOutException waiterTimedOutException) {
            logger.severe("Could not detect all instances are running!");
        }


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

        //Thread fullFib = new Thread(() -> logger.info("Full took: " + calculateFib(instanceIps.get(0), keyPath, calcFibFile, inputFullFile, outputFullFile, logger) + "ms"));
        Thread halfOneFib = new Thread(() -> logger.info("Half one took: " + calculateFib(instanceIps.get(1), keyPath, calcFibFile, inputHalfOneFile, outputHalfOneFile, logger) + "ms"));
        Thread halfTwoFib = new Thread(() -> logger.info("Half two took: " + calculateFib(instanceIps.get(2), keyPath, calcFibFile, inputHalfTwoFile, outputHalfTwoFile, logger) + "ms"));


        //fullFib.start();
        halfOneFib.start();
        halfTwoFib.start();

        /*
        for (int i = 0; i < instanceIps.size(); i++) {
            String ipAddress = instanceIps.get(i);
            logger.info(ipAddress);
            if (i == 0)
                logger.info("Full took: " + calculateFib(ipAddress, keyPath, calcFibFile, inputFullFile, outputFullFile, logger) + "ms");
            else if (i == 1)
                logger.info("Half one took: " + calculateFib(ipAddress, keyPath, calcFibFile, inputHalfOneFile, outputHalfOneFile, logger) + "ms");
            else
                logger.info("Half two took: " + calculateFib(ipAddress, keyPath, calcFibFile, inputHalfTwoFile, outputHalfTwoFile, logger) + "ms");
        }
        */

        //fullFib.join();
        halfOneFib.join();
        halfTwoFib.join();

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


    private static long calculateFib(String ipAddress, String keyPath, String calcFibFile, String inputFile, String outputFile, Logger logger) {

        long start = System.currentTimeMillis();

        String fileName = inputFile.substring(inputFile.lastIndexOf('/') + 1);
        String outputFileName = outputFile.substring(outputFile.indexOf('/') + 1);
        final String[] commands = {"sudo yum install -y -q java-1.8.0-openjdk" ,"java -jar calc_fib.jar " + fileName};

        try {
            logger.info("Sending calc_fib.jar to instance with IP: " + ipAddress);
            AWS_Utils.sendFileToInstance(ipAddress, keyPath, "scp -t calc_fib.jar", calcFibFile, logger);
            logger.info("Sending " + fileName + " to instance with IP: " + ipAddress);
            AWS_Utils.sendFileToInstance(ipAddress, keyPath, "scp -t " + fileName, inputFile, logger);
        } catch (Exception e) {
            logger.severe("Could not send file to instance with IP: " + ipAddress);
            logger.severe(e.toString());
        }
        for(String command : commands) {
            try {
                logger.info("Sending command " + command + " to instance with IP: " + ipAddress);
                AWS_Utils.sendSSHCommandToInstance(ipAddress, keyPath, command, logger, 10);
            } catch (Exception e) {
                logger.severe("Could not send command to instance with IP: " + ipAddress);
                logger.severe(e.toString());
            }
        }
        try {
            logger.info("Getting " + outputFileName + " from instance with IP: " + ipAddress);
            AWS_Utils.getFileFromInstance(ipAddress, keyPath, "scp -f output.csv", outputFile, logger);
        } catch (Exception e) {
            logger.severe("Could not get file from instance with IP: " + ipAddress);
            logger.severe(e.toString());
        }

        long end = System.currentTimeMillis();

        return end-start;

    }

}

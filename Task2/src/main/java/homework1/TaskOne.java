package homework1;

import aws.AWS_Utils;
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
 * @author Mathias Thoeni
 * @author David Freina
 */
public class TaskOne {

    public static void main(String[] args) {

        final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        final String securityGroupName = "ThoeniFreinaAWSAllowSSH";
        final String keyName = "thoeni-freina-key";

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
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(keyName)
                .withSecurityGroups(securityGroupName);

        long start = System.currentTimeMillis();
        long end = 1;

        RunInstancesResult result = amazonEC2Client.runInstances(runInstancesRequest);

        List<Instance> instances = result.getReservation().getInstances();

        List<String> instanceIds = instances.stream()
                .map(Instance::getInstanceId)
                .collect(Collectors.toList());

        //List<String> instanceIps = instances.stream().map(Instance::getPublicIpAddress).collect(Collectors.toList());

        logger.info("Waiting for instances to change state to running...");

        try {
            amazonEC2Client.waiters().instanceRunning().run(new WaiterParameters<>(new DescribeInstancesRequest().withInstanceIds(instanceIds)));
        } catch (WaiterTimedOutException waiterTimedOutException) {
            logger.severe("Could not detect all instances as running! Time measurements will be wrong!");
        }

        logger.info("Getting Instance IPs...");

        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest().withInstanceIds(instanceIds);

        List<String> instanceIps =
				amazonEC2Client.describeInstances(describeInstancesRequest).getReservations().stream().map(Reservation::getInstances).flatMap(List::stream).map(Instance::getPublicIpAddress).collect(Collectors.toList());

        instanceIps.forEach(ipAddress -> {
            logger.info(ipAddress);
            try {
                AWS_Utils.sendSSHCommandToInstance(ipAddress, "~/.ssh/thoeni-freina-key.pem", "ls -lah", logger, 10);
            } catch (Exception e) {
                logger.severe("Could not send command to instance with IP: " + ipAddress);
                logger.severe(e.toString());
            }
        });

        end = System.currentTimeMillis();

        logger.info("Time elapsed: " + (end - start));

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


}

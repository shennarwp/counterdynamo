package dh;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Counter
{
    private Logger logger;
    private Properties dynamodbProperties;
    private static final String COUNTER_TABLE_NAME = "counter";

    /** Default constructor */
    private Counter()
    {
        logger = LogManager.getLogger(this.getClass());
        loadProperties();
    }

    /** load the database information from the properties file */
    private void loadProperties()
    {
        try
        {
            this.dynamodbProperties = new Properties();
            dynamodbProperties.load(Counter.class.getResourceAsStream("/dynamodb.properties"));
        }
        catch (IOException ioe)
        {
            logger.error(String.format("Error: '%s' while loading the database info", ioe));
        }
    }

    /** connect to DynamoDB instance */
    private DynamoDB connectClient()
    {
        /* aws credentials, defined in properties file */
        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(
                dynamodbProperties.getProperty("aws.accessKeyId"),
                dynamodbProperties.getProperty("aws.secretAccessKey"));

        /* dynamo url and region configuration, also defined in properties file */
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        dynamodbProperties.getProperty("dynamodb.url"),
                        dynamodbProperties.getProperty("dynamodb.region")))
                .build();

        return new DynamoDB(client);
    }

    /** check if table exists in the DynamoDB client */
    private boolean isTableExist(DynamoDB dynamoDB, String tableName)
    {
        try
        {
            TableDescription tableDescription = dynamoDB.getTable(tableName).describe();
            logger.info("Table already exist: {} status: {}",
                    tableDescription.getTableName(),
                    tableDescription.getTableStatus());
            return true;
        } catch (com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException rnfe) {
            logger.info("Table does not exist yet...");
        }
        return false;
    }

    /** drop table if it exists in the DynamoDB client */
    private void dropTable(DynamoDB dynamoDB, String tableName)
    {
        if(isTableExist(dynamoDB, tableName))
        {
            Table table = dynamoDB.getTable(tableName);
            try
            {
                logger.info("Deleting table: {}, wait...", tableName);
                table.delete();
                table.waitForDelete();
                logger.info("Deleted table: {}", tableName);
            }
            catch (Exception e)
            {
                logger.info("Cannot delete table: {} : {}", tableName, e.getMessage());
            }
        }
    }

    /** create table if does not exist yet */
    private void createTable()
    {
        DynamoDB dynamoDB = connectClient();
        try
        {
            logger.info("Attempting to create table: {}; please wait...", COUNTER_TABLE_NAME);

            if(!isTableExist(dynamoDB, COUNTER_TABLE_NAME))
            {
                logger.info("Creating table...");
                Table newTable = dynamoDB.createTable(
                        COUNTER_TABLE_NAME,
                        Arrays.asList(
                                new KeySchemaElement("key", KeyType.HASH),      // Partition key
                                new KeySchemaElement("value", KeyType.RANGE)    // Sort key
                        ),
                        Arrays.asList(
                                new AttributeDefinition("key", ScalarAttributeType.N),
                                new AttributeDefinition("value", ScalarAttributeType.S)
                        ),
                        new ProvisionedThroughput(10L, 10L));

                newTable.waitForActive();
                logger.info("Success. status: {}", newTable.getDescription().getTableStatus());
            }
        }
        catch (Exception e)
        {
            logger.error("Unable to create table:\n{}", e.getMessage());
        }
    }

    /** count then save integer */
    private void count()
    {
        try
        {
            Integer i = 0;
            while (i < Integer.MAX_VALUE)
            {
                DynamoDB dynamoDB = connectClient();
                Table counterTable = dynamoDB.getTable(COUNTER_TABLE_NAME);
                counterTable.putItem(
                        new Item().withPrimaryKey("key", i)
                                  .withString("value", i.toString())
                );
                logger.info("Trying to insert {}", i);
                i++;
                Thread.sleep(2000);
            }
        }
        catch (InterruptedException ie)
        {
            Thread.currentThread().interrupt();
            logger.error(String.format("Error: '%s' . Program interrupted!", ie));
        }
    }

    public static void main(String... args)
    {
        Counter counter = new Counter();

        /* Clean up, recreate table */
        counter.dropTable(counter.connectClient(), COUNTER_TABLE_NAME);
        counter.createTable();

        counter.count();
    }
}

package com.linkedin.helix.integration;

import java.util.Date;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.linkedin.helix.DataAccessor;
import com.linkedin.helix.HelixManager;
import com.linkedin.helix.NotificationContext;
import com.linkedin.helix.PropertyType;
import com.linkedin.helix.TestHelper;
import com.linkedin.helix.ZNRecord;
import com.linkedin.helix.controller.HelixControllerMain;
import com.linkedin.helix.manager.zk.ZKDataAccessor;
import com.linkedin.helix.mock.storage.MockParticipant;
import com.linkedin.helix.mock.storage.MockTransition;
import com.linkedin.helix.model.Message;
import com.linkedin.helix.tools.ClusterSetup;
import com.linkedin.helix.tools.ClusterStateVerifier;

public class TestDummyAlerts extends ZkIntegrationTestBase
{
  public class DummyAlertsTransition extends MockTransition
  {
    @Override
    public void doTransition(Message message, NotificationContext context)
    {
      HelixManager manager = context.getManager();
      DataAccessor accessor = manager.getDataAccessor();
      String fromState = message.getFromState();
      String toState = message.getToState();
      String instance = message.getTgtName();
      String partition = message.getPartitionName();

      if (fromState.equalsIgnoreCase("SLAVE") && toState.equalsIgnoreCase("MASTER"))
      {
        for (int i = 0; i < 5; i++)
        {
          accessor.setProperty(PropertyType.HEALTHREPORT,
                               new ZNRecord("mockAlerts" + i),
                               instance,
                               "mockAlerts");
          try
          {
            Thread.sleep(1000);
          }
          catch (InterruptedException e)
          {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
      }
    }

  }

  @Test()
  public void testDummyAlerts() throws Exception
  {
    String clusterName = getShortClassName();
    MockParticipant[] participants = new MockParticipant[5];
    ClusterSetup setupTool = new ClusterSetup(ZK_ADDR);

    System.out.println("START TestDummyAlerts at " + new Date(System.currentTimeMillis()));

    TestHelper.setupCluster(clusterName, ZK_ADDR, 12918, // participant start
                                                         // port
                            "localhost", // participant name prefix
                            "TestDB", // resource name prefix
                            1, // resources
                            10, // partitions per resource
                            5, // number of nodes
                            3, // replicas
                            "MasterSlave",
                            true); // do rebalance

    enableHealthCheck(clusterName);
    setupTool.getClusterManagementTool()
             .addAlert(clusterName,
                       "EXP(decay(1.0)(*.defaultPerfCounters@defaultPerfCounters.availableCPUs))CMP(GREATER)CON(2)");

    TestHelper.startController(clusterName,
                               "controller_0",
                               ZK_ADDR,
                               HelixControllerMain.STANDALONE);
    // start participants
    for (int i = 0; i < 5; i++)
    {
      String instanceName = "localhost_" + (12918 + i);

      participants[i] =
          new MockParticipant(clusterName,
                              instanceName,
                              ZK_ADDR,
                              new DummyAlertsTransition());
      new Thread(participants[i]).start();
    }

    boolean result =
        ClusterStateVerifier.verify(new ClusterStateVerifier.BestPossAndExtViewZkVerifier(ZK_ADDR,
                                                                                          clusterName));
    Assert.assertTrue(result);

    // other verifications go here
    ZKDataAccessor accessor = new ZKDataAccessor(clusterName, _gZkClient);
    for (int i = 0; i < 5; i++)
    {
      String instance = "localhost_" + (12918 + i);
      ZNRecord record =
          accessor.getProperty(PropertyType.HEALTHREPORT, instance, "mockAlerts");
      Assert.assertEquals(record.getId(), "mockAlerts4");
    }

    // Thread.sleep(Long.MAX_VALUE);
    System.out.println("END TestDummyAlerts at " + new Date(System.currentTimeMillis()));
  }
}

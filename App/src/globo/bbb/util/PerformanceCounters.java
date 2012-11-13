package globo.bbb.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Armazena e manipula alguns indicadores de desempenho
 * @author wagner
 */
public class PerformanceCounters
{

  private static AtomicLong numReqCassandra = new AtomicLong();
  private static AtomicLong tempReqCassandra = new AtomicLong();
  
  private static AtomicLong numVoltes = new AtomicLong();
  private static AtomicLong tempVoltes = new AtomicLong();
  
  public static void addNewCassandraReqInfo(long temp)
  {
    tempReqCassandra.addAndGet(temp);
    numReqCassandra.incrementAndGet();
  }
  
  public static  void addNewVolteReqInfo(long temp)
  {
    tempVoltes.addAndGet(temp);
    numVoltes.incrementAndGet();
  }
  
  public static long getNumCassandraReqs()
  {
    return numReqCassandra.longValue();
  }
  
  public static float getCassandraAvgReqTime()
  {
    return tempReqCassandra.floatValue() / numReqCassandra.floatValue();
  }
  
  public static long getNumVoltes()
  {
    return numVoltes.longValue();
  }
  
  public static float getVolteAvgReqTime()
  {
    return tempVoltes.floatValue() / numVoltes.floatValue();
  }
  
}

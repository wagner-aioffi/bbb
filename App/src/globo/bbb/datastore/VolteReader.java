package globo.bbb.datastore;

import globo.bbb.datastore.pool.ConnectionPool;
import globo.bbb.datastore.pool.ConnectionPool.CassandraConnection;
import globo.bbb.util.PerformanceCounters;

import java.nio.ByteBuffer;

import org.apache.cassandra.thrift.ColumnOrSuperColumn;
import org.apache.cassandra.thrift.ColumnPath;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.NotFoundException;

/**
 * Componente responsável por ler o total de votos.
 * 
 * Possui a capacidade realizar leituras asincronas, em intervalos de tempo pré-determinados.
 * Nesse caso, o resultado parcial exibido para cada votante poderá estar defasado.
 * 
 * Os votos computados por esse servidor, no intervalo entre as leituras poderiam ser computados
 * em memória para aproximar melhor o resultado. Porém, isso causaria sincronizações de threads
 * na atualziação dos valores, podendo prejudicar o desempenho da aplicação.
 * 
 * @author wagner
 */
class VolteReader
{
  private long interval = 10000;
  private ConnectionPool pool;
  
  private float lastReadPercentual = 0.0f;

  VolteReader(ConnectionPool pool)
  {
    this.pool = pool;
    
    Thread t = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        while(true)
        {
          
          readVoltesCounters();
          
          try
          {
            Thread.sleep(interval);
          } catch (InterruptedException e)
          {
            throw new RuntimeException(e);
          }
        }
      }
    });
    t.setDaemon(true);
    t.setName("VolteReader");
    t.setPriority(Thread.MAX_PRIORITY);
    t.start();
    
  }
  
  float getContestant1Percentual()
  {
    return lastReadPercentual; 
  }
  
  private void readVoltesCounters()
  {
    CassandraConnection conn = pool.openConnection();
    try
    {
      /**
       * Como lemos os contadores em duas consultas, pode ocorrer
       * inconsistências devido a diferença temporal das leituras.
       */
      ColumnPath cpath = new ColumnPath("voltes");
      cpath.column = ByteBuffer.wrap(CassandraFacade.TOTAL_BYTES_COLUMN);

      long startTime = System.currentTimeMillis();
      ColumnOrSuperColumn column =
          conn.getClient().get(ByteBuffer.wrap(CassandraFacade.TOTAL_BYTES_COLUMN), cpath,
            ConsistencyLevel.ONE);
      PerformanceCounters.addNewCassandraReqInfo(System.currentTimeMillis()
        - startTime);

      long total = column.getCounter_column().value;

      cpath = new ColumnPath("voltes_1");
      cpath.column = ByteBuffer.wrap(CassandraFacade.TOTAL_BYTES_COLUMN);

      long total1 = 0;
      try
      {
        startTime = System.currentTimeMillis();
        column =
            conn.getClient().get(ByteBuffer.wrap(CassandraFacade.TOTAL_BYTES_COLUMN), cpath,
              ConsistencyLevel.ONE);
        PerformanceCounters.addNewCassandraReqInfo(System.currentTimeMillis()
          - startTime);

        total1 = column.getCounter_column().value;
      } catch (NotFoundException e)
      {
        // O contador ainda não existe, ou seja, o participante 1 tem 0 votos.
      }

      lastReadPercentual = (float) total1 / (float) total;
      
    } catch (Throwable e)
    {
      //Loga a exceção, porém não repassa ela para frente.
      //Evitando que a thread que lê os valores pare por
      //uma falha qualquer.
      e.printStackTrace();
    } finally
    {
      conn.close();
    }
  }


}

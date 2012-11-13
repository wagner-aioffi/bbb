package globo.bbb.datastore.pool;

import java.util.ArrayList;
import java.util.List;

import globo.bbb.datastore.pool.ConnectionPool.CassandraConnection;

import org.junit.Assert;
import org.junit.Test;

import junit.framework.TestCase;

/**
 * A classe ConnectionPool apresenta poucos métodos públicos.
 * Essa classe testa a lógica principal dela como uma caixa
 * preta.
 * 
 * @author wagner
 */
public class ConnectionPoolTest
{

  /**
   * Verifica se quando o pool estiver vazio, a
   * próxima chamada irá esperar até que uma conexão
   * seja liberada.
   */
  @Test
  public void testConnectionWait()
  {
    final ConnectionPool pool = new ConnectionPool(1);
    final CassandraConnection conn = pool.openConnection();
    
    //Pool está vazio
    //Essa lista será utilizada com um "flag" para indicar
    //que a thread abaixo liberou a conexão
    //Quando a conexão for liberada a lista terá um elemento.
    final List<Object> list = new ArrayList<Object>(1);

    Thread t = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        //Pagar garantir que o método de obter conexão do pool irá esperar.
        try
        {
          Thread.sleep(100);
        } catch (Exception e)
        {
          throw new RuntimeException(e);
        } 
        conn.close();
        list.add(1);
      }
    });
    t.start();

    pool.openConnection();
    
    Assert.assertTrue("A conexão ainda não estava liberada e o método conseguiu pegá-la", list.size() == 1);
    
  }

  
  
  
}
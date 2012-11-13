package globo.bbb.datastore.pool;

import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.Cassandra.Client;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

/**
 * Pool de conexão com o datasource Cassandra.
 * 
 * @author wagner
 */
public class ConnectionPool
{
  private static final Logger LOG = Logger.getLogger(ConnectionPool.class.getName());
  
  private static final int _9160 = 9160;

  private static final String LOCALHOST = "localhost";

  private static final String BBB = "bbb";
  
  /**
   * Número de conexões abertas no pool
   */
  private int numConnection;

  /**
   * Lista com as conexões livres para o uso da aplicação.
   */
  private LinkedList<CassandraConnection> freeConnections = new LinkedList<ConnectionPool.CassandraConnection>();
  
  public ConnectionPool(int numConnection)
  {
    this.numConnection = numConnection;
    start();
  }
  
  private void start()
  {
    LOG.info("Iniciando o pool de conexões");
    for(int i = 0; i < numConnection; i++)
    {
      //TODO Externalizar em um arquivo de configurações.
      freeConnections.add(new CassandraConnection(LOCALHOST, _9160, BBB));
    }
  }
  
  /**
   * Elimina uma conexão do pool e adiciona uma nova em seu lugar.
   * Esse método é utilizado quando uma conexão falha por algum motivo
   * e por segurança é substituída.
   * @param conn
   */
  public void replaceConnection(final CassandraConnection conn)
  {
    conn.closeClient();
    
    CassandraConnection newConn = new CassandraConnection(LOCALHOST, 9160, BBB);
    
    freeConnection(newConn);
  }
  
  /**
   * Pega uma conexão do pool de conexão.
   * 
   * OBSERVAÇÃO: Esse método pode bloquear a thread até que uma conexão esteja disponível.
   * 
   * @return
   */
  public CassandraConnection openConnection()
  {
    CassandraConnection conn = null;
    
    long startTime = System.currentTimeMillis();
    
    synchronized (freeConnections)
    {
      while(conn == null)
      {
          if (freeConnections.size() > 0)
          {
            conn = freeConnections.removeFirst();
            break;
          }
  
          //Aguarda até que uma conexão seja liberada.
          try
          {
            freeConnections.wait(10000);
          } catch (InterruptedException e)
          {
            LOG.info("Erro não esperado");
            throw new RuntimeException(e);
          }
      }
    }
    
    long enlapsedTime = System.currentTimeMillis() - startTime;
    if (enlapsedTime > 500)
    {
      LOG.severe("O pool de conexões está sobre carregado. Considere aumentar o seu tamanho.");
    }
    
    return conn;
  }
  
  
  private void freeConnection(final CassandraConnection conn)
  {
    synchronized (freeConnections)
    {
      freeConnections.add(conn);
      freeConnections.notify();
    }
  }
  
  /**
   * Uma conexão do Cassandra no pool.
   * @author wagner
   */
  public class CassandraConnection
  {

    private Client client;
    private TTransport tr;

    CassandraConnection(String server, int port, String keyspace)
    {
      tr = new TFramedTransport(new TSocket(LOCALHOST, 9160));
      TProtocol proto = new TBinaryProtocol(tr);
      client = new Cassandra.Client(proto);
      try
      {
        tr.open();
      } catch (TTransportException e)
      {
        LOG.log(Level.SEVERE, "Ocorreu um erro ao abrir a conexão com o Cassandra. Verifique se o servidor está no ar: " + server + ":" + port);
        throw new RuntimeException(e);
      }
      
      try
      {
        client.set_keyspace(keyspace);
      } catch (Exception e)
      {
        LOG.log(Level.SEVERE, "Ocorreu um erro ao acessar o keyspace da aplicação. Verifique o servidor cassandra. Keyspace=" + keyspace);
        throw new RuntimeException(e);
      }
      
    }
    
    private void closeClient()
    {
      tr.close();
    }

    /**
     * Devolve a conexão para o pool.
     */
    public void close()
    {
      freeConnection(this);
    }

    public Client getClient()
    {
      return client;
    }
  }

}

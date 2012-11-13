package globo.bbb.datastore;

import globo.bbb.datastore.pool.ConnectionPool;
import globo.bbb.datastore.pool.ConnectionPool.CassandraConnection;
import globo.bbb.util.PerformanceCounters;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.cassandra.thrift.ColumnOrSuperColumn;
import org.apache.cassandra.thrift.ColumnParent;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.CounterColumn;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.KeyRange;
import org.apache.cassandra.thrift.KeySlice;
import org.apache.cassandra.thrift.SlicePredicate;
import org.apache.cassandra.thrift.SliceRange;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.thrift.TException;

/**
 * Interface de acesso ao datasource da aplicação. Possui a responsabilidade de
 * conectar-se ao Cassandra e manipular os votos de cada um dos dois
 * participantes.
 * 
 * @author wagner
 */
public class CassandraFacade
{
  public static Charset charset = Charset.forName("UTF-8");

  public static CharsetDecoder decoder = charset.newDecoder();

  public static CharsetEncoder encoder = charset.newEncoder();

  private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH");

  private ConnectionPool connectionPool;

  private VolteReader volteReader;

  private long interval = 5000;

  private static CassandraFacade instance = new CassandraFacade();
  
  /**
   * Armazem em memória que contabiliza o número de votos dos participantes
   * ainda não enviados ao repositório. Os contadores não oficiais, são
   * atualizados em intervalos maiores de tempo, para evitar acessos ao
   * cassandra.
   */
  private AtomicLong totalVoltesContestant1 = new AtomicLong();

  private AtomicLong totalVoltes = new AtomicLong();

  static final byte[] TOTAL_BYTES_COLUMN;

  static
  {
    try
    {
      TOTAL_BYTES_COLUMN = "total".getBytes("utf-8");
    } catch (UnsupportedEncodingException e)
    {
      throw new RuntimeException(e);
    }
  }

  private CassandraFacade()
  {
    connectionPool = new ConnectionPool(100);
    volteReader = new VolteReader(connectionPool);
    startTotalUpdateThread();
  }

  /**
   * Recuperar e retorna o resultado da votação.
   * 
   * @return
   */
  public Result GetOfficialResult()
  {
    CassandraConnection conn = connectionPool.openConnection();

    Result result = new Result();

    try
    {
      getVoltesContestant(conn, result, 1);
      getVoltesContestant(conn, result, 2);

    } catch (Throwable e)
    {
      throw new RuntimeException(e);
    } finally
    {
      conn.close();
    }

    return result;
  }

  private void getVoltesContestant(CassandraConnection conn, Result result,
    int contestantID) throws InvalidRequestException, UnavailableException,
    TimedOutException, TException, CharacterCodingException, ParseException
  {
    final ColumnParent cp1 = new ColumnParent("voltes_" + contestantID);

    SlicePredicate sp1 = new SlicePredicate();
    SliceRange sr1 =
        new SliceRange(ByteBuffer.wrap(new byte[0]),
          ByteBuffer.wrap(new byte[0]), false, 1);
    sp1.slice_range = sr1;

    KeyRange kr = new KeyRange(720); // no máximo 1 mês de votação
    kr.start_key = ByteBuffer.wrap(new byte[0]);
    kr.end_key = ByteBuffer.wrap(new byte[0]);

    long startTime = System.currentTimeMillis();
    List<KeySlice> slices =
        conn.getClient().get_range_slices(cp1, sp1, kr, ConsistencyLevel.ALL);
    PerformanceCounters.addNewCassandraReqInfo(System.currentTimeMillis()
      - startTime);

    for (KeySlice slice : slices)
    {
      for (ColumnOrSuperColumn column : slice.columns)
      {
        String key = decoder.decode(column.getCounter_column().name).toString();
        if (!"total".equals(key))
        {
          long value = column.getCounter_column().value;
          result.setContestantVoltes(dateFormat.parse(key), contestantID, value);
        }
      }
    }
  }

  /**
   * Adiciona um voto ao participante identificado pelo ID e retorna o
   * percentual de votos do participante número 1
   * 
   * @param contestantID
   */
  public float addVoteContestant(final int contestantID)
  {
    CassandraConnection conn = connectionPool.openConnection();
    try
    {
      // Assumindo que todos os servidores estão com seus relogios sincronizados
      final Date now = new Date();
      final byte[] dateBytes = dateFormat.format(now).getBytes("utf-8");

      final ColumnParent cp = new ColumnParent("voltes_" + contestantID);

      // Adiciona o voto agrupado por data e hora.
      // Esse é o contador oficial, os próximos dois são redundantes e são
      // utilizados
      // para acelerar consultas. O resultado oficial deve ser baseado nesse
      // contador.
      addVote(conn, dateBytes, cp, 1);

      /**
       * Nesse ponto pode ocorrer incosistência nos contadores, já que a
       * instrução de acima pode executar, e por algum motivo a abaixo não. Por
       * isso, o contador acima deve ser utilizado como oficial para apuração.
       */
      incrementTotalCounters(contestantID);

      return volteReader.getContestant1Percentual();
    } catch (Exception e)
    {
      throw new RuntimeException(e);
    } finally
    {
      conn.close();
    }
  }

  private void incrementTotalCounters(int contestantID)
  {
    if (contestantID == 1)
    {
      totalVoltesContestant1.incrementAndGet();
    }
    totalVoltes.incrementAndGet();
  }

  private void updateTotalCounters()
  {
    final ColumnParent cpTotal = new ColumnParent("voltes");

    CassandraConnection conn = connectionPool.openConnection();
    try
    {

      final ColumnParent cp = new ColumnParent("voltes_1");

      long total = totalVoltes.getAndSet(0);
      long total1 = totalVoltesContestant1.getAndSet(0);

      // Adiciona o total de votos do concorrente 1
      if (total1 > 0)
      {
        addVote(conn, TOTAL_BYTES_COLUMN, cp, total1);
      }

      if (total > 0)
      {
        // Adiciona o total de votos (TOTAL)
        addVote(conn, TOTAL_BYTES_COLUMN, cpTotal, total);
      }

    } catch (Throwable e)
    {
      // Loga a exceção, porém não repassa ela para frente.
      // Evitando que a thread que lê os valores pare por
      // uma falha qualquer.
      e.printStackTrace();
    } finally
    {
      conn.close();
    }
  }

  private void addVote(final CassandraConnection conn, final byte[] dateBytes,
    final ColumnParent cp, long num) throws Exception
  {
    CounterColumn counter = new CounterColumn();
    ByteBuffer keyByte = ByteBuffer.wrap(dateBytes);
    counter.setName(keyByte);
    counter.setValue(num);

    long startTime = System.currentTimeMillis();
    conn.getClient().add(keyByte, cp, counter, ConsistencyLevel.ONE);
    PerformanceCounters.addNewCassandraReqInfo(System.currentTimeMillis()
      - startTime);
  }

  private void startTotalUpdateThread()
  {
    Thread t = new Thread(new Runnable()
    {

      @Override
      public void run()
      {
        while (true)
        {
          updateTotalCounters();

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
    t.setName("TotalVoltesUpdate");
    t.setPriority(Thread.MAX_PRIORITY);
    t.start();
  }
  
  /**
   * Recuperar a instância singleton do facade.
   * @return
   */
  public static CassandraFacade getInstance()
  {
    return instance;
  }
}

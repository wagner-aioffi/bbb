package globo.bbb;

import globo.bbb.datastore.CassandraFacade;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Não são testes de unidade. Foram utilizados para facilitar o desenvolvimento.
 * 
 *  Projeto com prazo super curto, testes foram sacrificados ;) 
 *  
 * @author wagner
 */
public class CassandraFacadeTest
{
  @Test
  @Ignore
  public void test()
  {
    CassandraFacade facade = CassandraFacade.getInstance();
    facade.addVoteContestant(1);
  }
  
  @Test
  public void testGetOfficialResult()
  {
    CassandraFacade facade = CassandraFacade.getInstance();
    facade.GetOfficialResult();
  }


}

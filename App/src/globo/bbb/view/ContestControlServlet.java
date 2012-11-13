package globo.bbb.view;

import globo.bbb.datastore.CassandraFacade;
import globo.bbb.util.PerformanceCounters;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.tanesha.recaptcha.ReCaptchaImpl;
import net.tanesha.recaptcha.ReCaptchaResponse;

/**
 * Servlet responsável por receber os votos
 *  
 * @author wagner
 *
 */
@WebServlet(name="vote", urlPatterns="/vote")
public class ContestControlServlet extends HttpServlet
{
	private boolean UNDER_LOAD_TEST = true;
	
	/**
   * Acesso direto ao data source.
   * 
   * TODO Refatorar, quando tiver tempo, para implementar uma camada
   * de controle intermediaria entre o datasource e a view. 
   */
	private CassandraFacade datasource;
	
	public ContestControlServlet()
	{
		datasource = CassandraFacade.getInstance();
	}

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException
  {
    long startTime = System.currentTimeMillis();

    //Valida o captcha evitando votos de robos
    if (!isHuman(req, resp)) return;
    	
    computeVote(req, resp);
    
    PerformanceCounters.addNewVolteReqInfo(System.currentTimeMillis() - startTime);    
  }

  private void computeVote(HttpServletRequest req, HttpServletResponse resp)
  {
    String vote = req.getParameter("v");
    if (vote == null || vote.length()==0)
    {
      //Erro nao correra com acesso correto ao sistema
      throwError("Errointerno", req, resp);
      return;
    }
    
    int numCotestant = -1;
    try
    {
      numCotestant = Integer.parseInt(vote);
    } catch (NumberFormatException e)
    {
      throwError("Errointerno", req, resp);
      return;
    }
    
    float perc;

    //Se tudo estiver OK computa o voto
    perc = datasource.addVoteContestant(numCotestant);
    
    try
    {
      resp.getWriter().write(Float.toString(perc));
    } catch (IOException e)
    {
    	throwError("ErroInterno", req, resp);
        return;
    }
  }

  private void throwError(String msg, HttpServletRequest req,
    HttpServletResponse resp)
  {
	  try 
	  {
		resp.getWriter().write(msg);
	} catch (IOException e) 
	{
		throw new RuntimeException(e);
	}
  }

  /**
   * Tenta identificar votacao realizada por robos
   * @param req
   */
  private boolean isHuman(HttpServletRequest req, HttpServletResponse resp)
  {
	  if (!UNDER_LOAD_TEST)
	  {
		  return validateCaptcha(req, resp);	  
	  }
	  else
	  {
		  //Na media o recapcth demora 167ms para retornar.
		  //Simula esse tempo para teste de carga
		  try 
		  {
			Thread.sleep(10);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		  return true;
	  }
    
    
    //TODO implementar solucao que identifique uma sequencia muito rapida
    //de votos vindo de uma mesma origem, que provavelmente não é humano.
    //Pensar como fica origens com NAT.
  }

  private boolean validateCaptcha(HttpServletRequest req, HttpServletResponse resp)
  {
	  String remoteAddr = req.getRemoteAddr();
	    ReCaptchaImpl reCaptcha = new ReCaptchaImpl();
	    
	    reCaptcha.setPrivateKey("6Le-BtkSAAAAAF6pG-3fCtpW9OUgkv6l22s-Nfgb");
	    
	    String challenge = req.getParameter("rcf");
	    String response = req.getParameter("rrf");
	    
	    long start = System.currentTimeMillis();
	    
	    ReCaptchaResponse reCaptchaResponse =
	        reCaptcha.checkAnswer(remoteAddr, challenge, response);
	    System.out.println(System.currentTimeMillis() - start);
	    
	    if (!reCaptchaResponse.isValid()) 
	    {
	    	throwError("ErroCaptcha", req, resp);
	    	return false;
	    }
	    else
	    {
	    	return true;
	    }
  }
}
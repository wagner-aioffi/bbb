package globo.bbb.datastore;

import java.util.Date;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Resultado apurado até o momento.
 */
public class Result
{
  private SortedMap<Date, PartResult> voltes = new TreeMap<Date, PartResult>();
  
  public SortedMap<Date, PartResult> getVoltes()
  {
    return voltes;
  }
  
  public void setContestantVoltes(Date date, int contestanID, long voltes)
  {
    if (contestanID == 1) getPartResult(date).contestant1 = voltes;
    else getPartResult(date).contestant2 = voltes;
  }
  
  private PartResult getPartResult(Date date)
  {
    PartResult result = voltes.get(date);
    if (result == null)
    {
      result = new PartResult();
      voltes.put(date, result);
    }
    return result;
  }
  
  public class PartResult
  {
    private long contestant1;
    private long contestant2;
    
    public long getContestant1()
    {
      return contestant1;
    }
    public void setContestant1(long contestant1)
    {
      this.contestant1 = contestant1;
    }
    public long getContestant2()
    {
      return contestant2;
    }
    public void setContestant2(long contestant2)
    {
      this.contestant2 = contestant2;
    }
    
    
  }
}

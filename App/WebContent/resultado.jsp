<%@page import="java.util.Map.Entry"%>
<%@page import="globo.bbb.datastore.Result.PartResult"%>
<%@page import="java.util.Date"%>
<%@page import="java.util.SortedMap"%>
<%@page import="globo.bbb.datastore.CassandraFacade"%>
<%@page import="globo.bbb.datastore.Result"%>
<%@page import="globo.bbb.util.PerformanceCounters"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Resultado</title>
</head>
<body>

<h1>Indicadores de desempenho do servidor</h1>

<table>
	<tr>
		<td>Número requisições cassandra</td>
		<td><%=PerformanceCounters.getNumCassandraReqs()%></td>
	</tr>
	<tr>
		<td>Tempo médio de acesso ao cassandra</td>
		<td><%=PerformanceCounters.getCassandraAvgReqTime()%></td>
	</tr>
		<tr>
		<td>Número requisições de votos</td>
		<td><%=PerformanceCounters.getNumVoltes()%></td>
	</tr>
	<tr>
		<td>Tempo médio de requisição de voto</td>
		<td><%=PerformanceCounters.getVolteAvgReqTime()%></td>
	</tr>
</table>

<h1>Resultado da votação</h1>

<table border="1">
	<tr><td>Dia-hora</td><td>Participante 1</td><td>Participante 2</td></tr>

	<%
		Result result = CassandraFacade.getInstance().GetOfficialResult();
		SortedMap<Date, PartResult> voltes = result.getVoltes();
		long total1 = 0;
		long total2 = 0;
		for(Entry<Date, PartResult> entry : voltes.entrySet())
		{
		  total1 += entry.getValue().getContestant1();
		  total2 += entry.getValue().getContestant2();
	%>
		<tr><td><%=entry.getKey().toLocaleString() %></td><td><%=entry.getValue().getContestant1()%></td><td><%=entry.getValue().getContestant2()%></td></tr>
	<%
		}
	%>	
	<tr><td>TOTAL</td><td><%=total1%></td><td><%=total2%></td></tr>
</table>

</body>
</html>
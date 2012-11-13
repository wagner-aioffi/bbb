all: configureEnv cassandra tomcat

configureEnv:
	export JAVA_HOME=`pwd`/jre1.7.0_09
	export PATH=$JAVA_HOME/bin:$PATH
	

cassandra:
	echo "Iniciando o Cassandra ..."
	bash startCassandra.sh

tomcat:
	echo "Iniciando o tomcat..."
	apache-tomcat-7.0.22/bin/startup.sh
stop:
	kill -9 `cat pid.txt`
	apache-tomcat-7.0.22/bin/shutdown.sh

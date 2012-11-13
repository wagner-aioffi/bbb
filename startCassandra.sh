#!/bin/sh
export JAVA_HOME=`pwd`/jre1.7.0_09
export PATH=$JAVA_HOME/bin:$PATH
apache-cassandra-1.1.6/bin/cassandra -p pid.txt

FROM jboss/wildfly
ENV JBOSS_CLI /opt/jboss/wildfly/bin/jboss-cli.sh
ENV LC_CTYPE=en_US.UTF-8

# Folder and .db file for SQLite Datasource
RUN mkdir /opt/jboss/sqlite/
RUN touch /opt/jboss/sqlite/main.db

USER root
RUN chown 755 /opt/jboss/wildfly/standalone/

RUN /opt/jboss/wildfly/bin/add-user.sh admin Admin --silent

RUN /bin/sh -c '/opt/jboss/wildfly/bin/standalone.sh -c standalone-full.xml -b 127.0.0.1 -bmanagement 0.0.0.0 &' && \
      sleep 10 && \
      # Add JMS Queue
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --connect --command="jms-queue add --queue-address=ordersQueue --entries=[queue/ordersQueue jms/queue/ordersQueue java:jboss/exported/jms/queue/ordersQueue]" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --connect --command="jms-queue add --queue-address=secondQueue --entries=[queue/secondQueue jms/queue/secondQueue java:jboss/exported/jms/queue/secondQueue]" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --connect --command="jms-queue add --queue-address=thirdQueue --entries=[queue/thirdQueue jms/queue/thirdQueue java:jboss/exported/jms/queue/thirdQueue]" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --connect --command="jms-queue add --queue-address=fourthQueue --entries=[queue/fourthQueue jms/queue/fourthQueue java:jboss/exported/jms/queue/fourthQueue]" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --connect --command="jms-queue add --queue-address=fifthQueue --entries=[queue/fifthQueue jms/queue/fifthQueue java:jboss/exported/jms/queue/fifthQueue]" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --connect --command="jms-queue add --queue-address=sixthQueue --entries=[queue/sixthQueue jms/queue/sixthQueue java:jboss/exported/jms/queue/sixthQueue]" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --connect --command="jms-queue add --queue-address=seventhQueue --entries=[queue/seventhQueue jms/queue/seventhQueue java:jboss/exported/jms/queue/seventhQueue]" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --connect --command="jms-queue add --queue-address=eighthQueue --entries=[queue/eighthQueue jms/queue/eighthQueue java:jboss/exported/jms/queue/eighthQueue]" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --connect --command="/subsystem=messaging-activemq/server=default/connection-factory=QueueConnectionFactory:add(entries=[java:/QueueConnectionFactory],connectors=[in-vm])" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --connect --command="/subsystem=messaging-activemq/server=default/connection-factory=QueueConnectionFactory:write-attribute(name=thread-pool-max-size,value=40)" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --command="/subsystem=messaging-activemq/server=default:write-attribute(name=statistics-enabled,value=true)" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --command="/subsystem=messaging-activemq/server=default/pooled-connection-factory=activemq-ra:write-attribute(name=statistics-enabled,value=true)" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --command="/subsystem=ejb3/strict-max-bean-instance-pool=mdb-strict-max-pool:write-attribute(name=derive-size, value=none)" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --command="/subsystem=ejb3/strict-max-bean-instance-pool=mdb-strict-max-pool:write-attribute(name=max-pool-size, value=10)" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --connect --command="/:reload" && \
      # SQLite driver configuration ===>
      # Download sqlite driver
      curl --location --output /opt/jboss/sqlite-jdbc-3.32.3.2.jar --url https://github.com/xerial/sqlite-jdbc/releases/download/3.32.3.2/sqlite-jdbc-3.32.3.2.jar && \
      # Add driver and create datasource
      $JBOSS_CLI --command="module add --name=org.sqlite --resources=sqlite-jdbc-3.32.3.2.jar --dependencies=javax.api,javax.transaction.api" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --command="/subsystem=datasources/jdbc-driver=sqlite:add(driver-name=sqlite,driver-module-name=org.sqlite,driver-class-name=org.sqlite.JDBC)" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --command="data-source add --jndi-name=java:jboss/datasources/SqliteDS --name=SqliteDS --connection-url=jdbc:sqlite:/opt/jboss/sqlite/main.db --driver-name=sqlite" && \
      # Add logger level
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --command="/subsystem=logging/root-logger=ROOT:change-root-log-level(level=SEVERE)" && \
      # Add timeout for transactions (300 sec)
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --command="/subsystem=transactions:write-attribute(name=default-timeout,value=300)" && \
      # Statistic
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --command="/subsystem=datasources/data-source=SqliteDS:write-attribute(name=statistics-enabled, value=true)" && \
      # ===> PostgreSQL driver configuration
      # Download driver
      curl --location --output /opt/jboss/postgresql-42.2.24.jar --url https://jdbc.postgresql.org/download/postgresql-42.2.24.jar && \
      # Add driver and create datasource
      $JBOSS_CLI --command="module add --name=org.postgresql --resources=postgresql-42.2.24.jar --dependencies=javax.api,javax.transaction.api" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --command="/subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql,driver-module-name=org.postgresql,driver-xa-datasource-class-name=org.postgresql.xa.PGXADataSource)" && \
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --command="data-source add --name=PostgresDS --jndi-name=java:jboss/PostgresDS --driver-name=postgresql --connection-url=jdbc:postgresql://postgres:5432/builder --user-name=postgres --password=admin --validate-on-match=true --background-validation=false --valid-connection-checker-class-name=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLValidConnectionChecker --exception-sorter-class-name=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLExceptionSorter" && \
      # Statistic
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --command="/subsystem=datasources/data-source=PostgresDS:write-attribute(name=statistics-enabled, value=true)" && \
      # Shutdown and delete tmp files
      $JBOSS_CLI -c --controller=http-remoting://localhost:9990 --connect --command=:shutdown && \
      rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history/current/* && \
      rm -f /opt/jboss/*.jar

ADD /builder-bot-ear/target/*.ear /opt/jboss/wildfly/standalone/deployments/
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-c", "standalone-full.xml", "-b", "127.0.0.1","-bmanagement","0.0.0.0"]
# Start a RMI registry with the CLASSPATH variable defined
# to the folder where we have .class files.
# Otherwise, we'll get java.lang.ClassNotFoundException: poneytoponey.Identity (no security manager: RMI class loader disabled)
#
# NOTE: not useful anymore as embedded in clients !
# rmi:
#     echo Starting Java RMI server, just hit Ctrl+c here to stop it.
#     CLASSPATH=app/bin/main/ rmiregistry 7000

build:
    javac -d app/src/main/java/build app/src/main/java/*/*.java

client ip:
    echo Starting another client !
    just build
    java  -classpath app/src/main/java/build poneytoponey.App http://{{ip}}:8080

lena ip:
    echo Starting another client !
    just build
    java  -Djava.rmi.server.hostname=10.128.31.36 -classpath app/src/main/java/build poneytoponey.App http://{{ip}}:8080

sam ip:
    echo Starting another client !
    just build
    java  -Djava.rmi.server.hostname=10.128.31.73 -classpath app/src/main/java/build poneytoponey.App http://{{ip}}:8080

kylian ip:
    echo Starting another client !
    just build
    java  -Djava.rmi.server.hostname=10.128.31.48 -classpath app/src/main/java/build poneytoponey.App http://{{ip}}:8080

ileane ip:
    echo Starting another client !
    just build
    java  -Djava.rmi.server.hostname=10.206.13.219 -classpath app/src/main/java/build poneytoponey.App http://{{ip}}:8080

directory:
    echo Starting the directory server !
    just build
    java -classpath app/src/main/java/build directory.DirectoryServer

faulty-leave ip username:
    echo Running a client that sends a bad leave signature !
    just build
    javac -classpath app/src/main/java/build -d app/src/test/java/build app/src/test/java/poneytoponey/FaultyDirectory.java app/src/test/java/poneytoponey/FaultyLeaveCheck.java
    java -classpath "app/src/main/java/build;app/src/test/java/build" poneytoponey.FaultyLeaveCheck http://{{ip}}:8080 {{username}}


cryptocheck:
    echo Running basic pseudo unit tests for RSA class
    just build
    javac -classpath app/src/main/java/build -d app/src/test/java/build app/src/test/java/poneytoponey/RSACryptoCheck.java
    java -classpath "app/src/main/java/build:app/src/test/java/build" poneytoponey.RSACryptoCheck

del zsk\*.class

javac -cp lib/commons-lang3-3.0.jar;lib/commons-configuration-1.6.jar;lib/commons-collections-3.2.1.jar;lib/commons-lang-2.4.jar;lib/commons-logging-1.1.1.jar;lib/commons-digester-1.8.jar;lib/commons-beanutils-1.7.0.jar;lib/commons-beanutils-core-1.8.0.jar;lib/commons-cli-1.2.jar;lib/httpclient-4.3.5.jar;lib/httpcore-4.3.2.jar;lib/commons-codec-1.6.jar;. zsk/*.java -encoding utf8

jar cmvf META-INF/manifest.mf ytd.jar zsk/*.class resources/zsk/images/* lib/*

del zsk\*.class

pause

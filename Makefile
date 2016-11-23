compile:
	sbt 'project supervisor' compile

test:
	sbt 'project supervisor' test

testOnly:
	sbt 'project supervisor' 'testOnly *$(SPEC_CLASS) -- -oF'

coverage:
	sbt 'project supervisor' clean coverage test
	open supervisor/target/scala-2.12/scoverage-report/index.html

release:
	sbt 'project supervisor' publishSigned
	sbt 'project supervisor' sonatypeRelease



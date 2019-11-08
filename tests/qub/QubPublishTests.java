package qub;

public interface QubPublishTests
{
    static void test(TestRunner runner)
    {
        runner.testGroup(QubPublish.class, () ->
        {
            runner.testGroup("setQubPack(QubPack)", () ->
            {
                runner.test("with null", (Test test) ->
                {
                    final QubPublish qubPublish = new QubPublish();
                    test.assertSame(qubPublish, qubPublish.setQubPack(null));
                    final QubPack qubPack = qubPublish.getQubPack();
                    test.assertNotNull(qubPack);
                    test.assertSame(qubPack, qubPublish.getQubPack());
                });

                runner.test("with non-null", (Test test) ->
                {
                    final QubPublish qubPublish = new QubPublish();
                    final QubPack qubPack = qubPublish.getQubPack();
                    test.assertSame(qubPublish, qubPublish.setQubPack(qubPack));
                    test.assertSame(qubPack, qubPublish.getQubPack());
                });
            });

            runner.testGroup("main(String[])", () ->
            {
                runner.test("with null", (Test test) ->
                {
                    test.assertThrows(new PreConditionFailure("args cannot be null."),
                        () -> QubPublish.main((String[])null));
                });
            });

            runner.testGroup("main(Console)", () ->
            {
                runner.test("with null", (Test test) ->
                {
                    test.assertThrows(new PreConditionFailure("console cannot be null."),
                        () -> main((Console)null));
                });

                runner.test("with \"-?\"", (Test test) ->
                {
                    final InMemoryByteStream output = new InMemoryByteStream();
                    final InMemoryByteStream error = new InMemoryByteStream();
                    try (final Console console = new Console(CommandLineArguments.create("-?")))
                    {
                        console.setOutputByteWriteStream(output);
                        console.setErrorByteWriteStream(error);

                        main(console);
                        test.assertEqual(-1, console.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Usage: qub-publish [[--folder=]<folder-to-publish>] [--jvm.classpath=<jvm.classpath-value>] [--testjson] [--coverage[=<None|Sources|Tests|All>]] [--buildjson] [--warnings=<show|error|hide>] [--verbose] [--profiler] [--help]",
                            "  Used to published packaged source and compiled code to the qub folder.",
                            "  --folder: The folder to publish. Defaults to the current folder.",
                            "  --jvm.classpath: The classpath that was passed to the JVM when this application was started.",
                            "  --testjson: Whether or not to write the test results to a test.json file.",
                            "  --coverage(c): Whether or not to collect code coverage information while running tests.",
                            "  --buildjson: Whether or not to read and write a build.json file. Defaults to true.",
                            "  --warnings: How to handle build warnings. Can be either \"show\", \"error\", or \"hide\". Defaults to \"show\".",
                            "  --verbose(v): Whether or not to show verbose logs.",
                            "  --profiler: Whether or not this application should pause before it is run to allow a profiler to be attached.",
                            "  --help(?): Show the help message for this application."),
                        Strings.getLines(output.asCharacterReadStream().getText().await()));
                    test.assertEqual("", error.asCharacterReadStream().getText().await());
                });

                runner.test("with no QUB_HOME specified", (Test test) ->
                {
                    final InMemoryByteStream output = new InMemoryByteStream();
                    final InMemoryByteStream error = new InMemoryByteStream();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = new ProjectJSON();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(new ProjectJSONJava());
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        JSON.object(projectJSON::write).toString()).await();
                    try (final Console console = new Console())
                    {
                        console.setOutputByteWriteStream(output);
                        console.setErrorByteWriteStream(error);
                        console.setFileSystem(fileSystem);
                        console.setCurrentFolderPath(Path.parse("/"));
                        console.setEnvironmentVariables(new EnvironmentVariables());

                        main(console);
                        test.assertEqual(1, console.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "ERROR: Can't publish without a QUB_HOME environment variable."
                        ),
                        Strings.getLines(output.asCharacterReadStream().getText().await()).skipLast());
                    test.assertEqual("", error.asCharacterReadStream().getText().await());
                });

                runner.test("with failed QubPack", (Test test) ->
                {
                    final InMemoryByteStream output = new InMemoryByteStream();
                    final InMemoryByteStream error = new InMemoryByteStream();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = new ProjectJSON();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(new ProjectJSONJava());
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        JSON.object(projectJSON::write).toString()).await();
                    try (final Console console = new Console())
                    {
                        console.setOutputByteWriteStream(output);
                        console.setErrorByteWriteStream(error);
                        console.setFileSystem(fileSystem);
                        console.setCurrentFolderPath(Path.parse("/"));
                        console.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = console.getCurrentFolder().await();
                        console.setProcessFactory(new FakeProcessFactory(console.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath("/outputs")
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath("/outputs")
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder("/outputs")
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject"))
                                .setFunction(1)));

                        final QubPack qubPack = new QubPack();
                        qubPack.setJarCreator(new FakeJarCreator());

                        main(console, qubPack);
                        
                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                ""
                            ),
                            Strings.getLines(output.asCharacterReadStream().getText().await()).skipLast());
                        test.assertEqual("", error.asCharacterReadStream().getText().await());
                    
                        test.assertEqual(1, console.getExitCode());
                    }
                });

                runner.test("with already existing version folder", (Test test) ->
                {
                    final InMemoryByteStream output = new InMemoryByteStream();
                    final InMemoryByteStream error = new InMemoryByteStream();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/me/my-project/1/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = new ProjectJSON();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(new ProjectJSONJava());
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        JSON.object(projectJSON::write).toString()).await();
                    try (final Console console = new Console())
                    {
                        console.setOutputByteWriteStream(output);
                        console.setErrorByteWriteStream(error);
                        console.setFileSystem(fileSystem);
                        console.setCurrentFolderPath(Path.parse("/"));
                        console.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = console.getCurrentFolder().await();
                        console.setProcessFactory(new FakeProcessFactory(console.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath("/outputs")
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath("/outputs")
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder("/outputs")
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject"))));

                        main(console);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "ERROR: This package (me/my-project:1) can't be published because a package with that signature already exists."
                            ),
                            Strings.getLines(output.asCharacterReadStream().getText().await()).skipLast());
                        test.assertEqual("", error.asCharacterReadStream().getText().await());

                        test.assertEqual(1, console.getExitCode());
                    }
                    test.assertFalse(fileSystem.fileExists("/qub/me/my-project/1/my-project.jar").await());
                    test.assertFalse(fileSystem.fileExists("/qub/me/my-project/1/my-project.sources.jar").await());
                    test.assertFalse(fileSystem.fileExists("/qub/me/my-project/1/project.json").await());
                    test.assertFalse(fileSystem.fileExists("/qub/my-project.cmd").await());
                });

                runner.test("with simple success scenario", (Test test) ->
                {
                    final InMemoryByteStream output = new InMemoryByteStream();
                    final InMemoryByteStream error = new InMemoryByteStream();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = new ProjectJSON();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(new ProjectJSONJava());
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        JSON.object(projectJSON::write).toString()).await();
                    try (final Console console = new Console())
                    {
                        console.setOutputByteWriteStream(output);
                        console.setErrorByteWriteStream(error);
                        console.setFileSystem(fileSystem);
                        console.setCurrentFolderPath(Path.parse("/"));
                        console.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = console.getCurrentFolder().await();
                        console.setProcessFactory(new FakeProcessFactory(console.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath("/outputs")
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath("/outputs")
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder("/outputs")
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject"))));

                        main(console);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/my-project@1..."
                            ),
                            Strings.getLines(output.asCharacterReadStream().getText().await()).skipLast());
                        test.assertEqual("", error.asCharacterReadStream().getText().await());

                        test.assertEqual(0, console.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Files:",
                            "MyProject.class",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/1/my-project.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Files:",
                            "MyProject.java",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/1/my-project.sources.jar").await()));
                    test.assertEqual(
                        "{\"publisher\":\"me\",\"project\":\"my-project\",\"version\":\"1\",\"java\":{}}",
                        fileSystem.getFileContentAsString("/qub/me/my-project/1/project.json").await());
                    test.assertFalse(fileSystem.fileExists("/qub/my-project.cmd").await());
                });

                runner.test("with mainClass in project.json", (Test test) ->
                {
                    final InMemoryByteStream output = new InMemoryByteStream();
                    final InMemoryByteStream error = new InMemoryByteStream();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = new ProjectJSON()
                        .setProject("my-project")
                        .setPublisher("me")
                        .setVersion("1")
                        .setJava(new ProjectJSONJava()
                            .setMainClass("MyProject"));
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        JSON.object(projectJSON::write).toString()).await();
                    try (final Console console = new Console())
                    {
                        console.setOutputByteWriteStream(output);
                        console.setErrorByteWriteStream(error);
                        console.setFileSystem(fileSystem);
                        console.setCurrentFolderPath(Path.parse("/"));
                        console.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = console.getCurrentFolder().await();
                        console.setProcessFactory(new FakeProcessFactory(console.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath("/outputs")
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath("/outputs")
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder("/outputs")
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject"))));

                        main(console);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/my-project@1..."
                            ),
                            Strings.getLines(output.asCharacterReadStream().getText().await()).skipLast());
                        test.assertEqual("", error.asCharacterReadStream().getText().await());

                        test.assertEqual(0, console.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Manifest file:",
                            "META-INF/MANIFEST.MF",
                            "",
                            "Files:",
                            "MyProject.class",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/1/my-project.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Files:",
                            "MyProject.java",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/1/my-project.sources.jar").await()));
                    test.assertEqual(
                        "{\"publisher\":\"me\",\"project\":\"my-project\",\"version\":\"1\",\"java\":{\"mainClass\":\"MyProject\"}}",
                        fileSystem.getFileContentAsString("/qub/me/my-project/1/project.json").await());
                    test.assertEqual(
                        Iterable.create(
                            "@echo OFF",
                            "java -classpath %~dp0me/my-project/1/my-project.jar MyProject %*"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/my-project.cmd").await()));
                });

                runner.test("with mainClass and captureVMArguments in project.json", (Test test) ->
                {
                    final InMemoryByteStream output = new InMemoryByteStream();
                    final InMemoryByteStream error = new InMemoryByteStream();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = new ProjectJSON();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(new ProjectJSONJava()
                        .setMainClass("MyProject")
                        .setCaptureVMArguments(true));
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        JSON.object(projectJSON::write).toString()).await();
                    try (final Console console = new Console())
                    {
                        console.setOutputByteWriteStream(output);
                        console.setErrorByteWriteStream(error);
                        console.setFileSystem(fileSystem);
                        console.setCurrentFolderPath(Path.parse("/"));
                        console.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = console.getCurrentFolder().await();
                        console.setProcessFactory(new FakeProcessFactory(console.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath("/outputs")
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath("/outputs")
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder("/outputs")
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject"))));

                        main(console);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/my-project@1..."
                            ),
                            Strings.getLines(output.asCharacterReadStream().getText().await()).skipLast());
                        test.assertEqual("", error.asCharacterReadStream().getText().await());

                        test.assertEqual(0, console.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Manifest file:",
                            "META-INF/MANIFEST.MF",
                            "",
                            "Files:",
                            "MyProject.class",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/1/my-project.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Files:",
                            "MyProject.java",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/1/my-project.sources.jar").await()));
                    test.assertEqual(
                        "{\"publisher\":\"me\",\"project\":\"my-project\",\"version\":\"1\",\"java\":{\"mainClass\":\"MyProject\",\"captureVMArguments\":true}}",
                        fileSystem.getFileContentAsString("/qub/me/my-project/1/project.json").await());
                    test.assertEqual(
                        Iterable.create(
                            "@echo OFF",
                            "java -classpath %~dp0me/my-project/1/my-project.jar MyProject --jvm.classpath=%~dp0me/my-project/1/my-project.jar %*"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/my-project.cmd").await()));
                });

                runner.test("with mainClass and dependencies in project.json", (Test test) ->
                {
                    final InMemoryByteStream output = new InMemoryByteStream();
                    final InMemoryByteStream error = new InMemoryByteStream();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = new ProjectJSON();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(new ProjectJSONJava()
                        .setMainClass("MyProject")
                        .setDependencies(Iterable.create(
                            new Dependency()
                                .setPublisher("me")
                                .setProject("my-other-project")
                                .setVersion("5"),
                            new Dependency()
                                .setPublisher("you")
                                .setProject("stuff")
                                .setVersion("7.3.1")
                        )));
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        JSON.object(projectJSON::write).toString()).await();
                    fileSystem.setFileContentAsString("/qub/me/my-other-project/5/my-other-project.jar", "hello").await();
                    fileSystem.setFileContentAsString("/qub/you/stuff/7.3.1/stuff.jar", "hello2").await();
                    try (final Console console = new Console())
                    {
                        console.setOutputByteWriteStream(output);
                        console.setErrorByteWriteStream(error);
                        console.setFileSystem(fileSystem);
                        console.setCurrentFolderPath(Path.parse("/"));
                        console.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = console.getCurrentFolder().await();
                        console.setProcessFactory(new FakeProcessFactory(console.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath(Iterable.create(
                                    "/outputs",
                                    "/qub/me/my-other-project/5/my-other-project.jar",
                                    "/qub/you/stuff/7.3.1/stuff.jar"))
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath("/outputs;/qub/me/my-other-project/5/my-other-project.jar;/qub/you/stuff/7.3.1/stuff.jar")
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder("/outputs")
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject"))));

                        main(console);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/my-project@1..."
                            ),
                            Strings.getLines(output.asCharacterReadStream().getText().await()).skipLast());
                        test.assertEqual("", error.asCharacterReadStream().getText().await());

                        test.assertEqual(0, console.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Manifest file:",
                            "META-INF/MANIFEST.MF",
                            "",
                            "Files:",
                            "MyProject.class",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/1/my-project.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Files:",
                            "MyProject.java",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/1/my-project.sources.jar").await()));
                    test.assertEqual(
                        "{\"publisher\":\"me\",\"project\":\"my-project\",\"version\":\"1\",\"java\":{\"mainClass\":\"MyProject\",\"dependencies\":[{\"publisher\":\"me\",\"project\":\"my-other-project\",\"version\":\"5\"},{\"publisher\":\"you\",\"project\":\"stuff\",\"version\":\"7.3.1\"}]}}",
                        fileSystem.getFileContentAsString("/qub/me/my-project/1/project.json").await());
                    test.assertEqual(
                        Iterable.create(
                            "@echo OFF",
                            "java -classpath %~dp0me/my-project/1/my-project.jar;%~dp0me/my-other-project/5/my-other-project.jar;%~dp0you/stuff/7.3.1/stuff.jar MyProject %*"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/my-project.cmd").await()));
                });

                runner.test("with mainClass, captureVMArguments, and dependencies in project.json", (Test test) ->
                {
                    final InMemoryByteStream output = new InMemoryByteStream();
                    final InMemoryByteStream error = new InMemoryByteStream();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = new ProjectJSON()
                        .setProject("my-project")
                        .setPublisher("me")
                        .setVersion("1")
                        .setJava(new ProjectJSONJava()
                            .setMainClass("MyProject")
                            .setCaptureVMArguments(true)
                            .setDependencies(Iterable.create(
                                new Dependency()
                                    .setPublisher("me")
                                    .setProject("my-other-project")
                                    .setVersion("5"),
                                new Dependency()
                                    .setPublisher("you")
                                    .setProject("stuff")
                                    .setVersion("7.3.1"))));
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        JSON.object(projectJSON::write).toString()).await();
                    fileSystem.setFileContentAsString("/qub/me/my-other-project/5/my-other-project.jar", "hello").await();
                    fileSystem.setFileContentAsString("/qub/you/stuff/7.3.1/stuff.jar", "hello2").await();
                    try (final Console console = new Console())
                    {
                        console.setOutputByteWriteStream(output);
                        console.setErrorByteWriteStream(error);
                        console.setFileSystem(fileSystem);
                        console.setCurrentFolderPath(Path.parse("/"));
                        console.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = console.getCurrentFolder().await();
                        console.setProcessFactory(new FakeProcessFactory(console.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath(Iterable.create(
                                    "/outputs",
                                    "/qub/me/my-other-project/5/my-other-project.jar",
                                    "/qub/you/stuff/7.3.1/stuff.jar"))
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath("/outputs;/qub/me/my-other-project/5/my-other-project.jar;/qub/you/stuff/7.3.1/stuff.jar")
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder("/outputs")
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject"))));

                        main(console);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/my-project@1..."
                            ),
                            Strings.getLines(output.asCharacterReadStream().getText().await()).skipLast());
                        test.assertEqual("", error.asCharacterReadStream().getText().await());

                        test.assertEqual(0, console.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Manifest file:",
                            "META-INF/MANIFEST.MF",
                            "",
                            "Files:",
                            "MyProject.class",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/1/my-project.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Files:",
                            "MyProject.java",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/1/my-project.sources.jar").await()));
                    test.assertEqual(
                        "{\"publisher\":\"me\",\"project\":\"my-project\",\"version\":\"1\",\"java\":{\"mainClass\":\"MyProject\",\"captureVMArguments\":true,\"dependencies\":[{\"publisher\":\"me\",\"project\":\"my-other-project\",\"version\":\"5\"},{\"publisher\":\"you\",\"project\":\"stuff\",\"version\":\"7.3.1\"}]}}",
                        fileSystem.getFileContentAsString("/qub/me/my-project/1/project.json").await());
                    test.assertEqual(
                        Iterable.create(
                            "@echo OFF",
                            "java -classpath %~dp0me/my-project/1/my-project.jar;%~dp0me/my-other-project/5/my-other-project.jar;%~dp0you/stuff/7.3.1/stuff.jar MyProject --jvm.classpath=%~dp0me/my-project/1/my-project.jar;%~dp0me/my-other-project/5/my-other-project.jar;%~dp0you/stuff/7.3.1/stuff.jar %*"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/my-project.cmd").await()));
                });

                runner.test("with mainClass and transitive dependencies in project.json", (Test test) ->
                {
                    final InMemoryByteStream output = new InMemoryByteStream();
                    final InMemoryByteStream error = new InMemoryByteStream();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON aProjectJSON = new ProjectJSON()
                        .setProject("a")
                        .setPublisher("me")
                        .setVersion("1")
                        .setJava(new ProjectJSONJava()
                            .setMainClass("MyProject")
                            .setDependencies(Iterable.create(
                                new Dependency()
                                    .setPublisher("me")
                                    .setProject("b")
                                    .setVersion("5"))));
                    final ProjectJSON bProjectJSON = new ProjectJSON()
                        .setProject("b")
                        .setPublisher("me")
                        .setVersion("5")
                        .setJava(new ProjectJSONJava()
                            .setDependencies(Iterable.create(
                                new Dependency()
                                    .setPublisher("me")
                                    .setProject("c")
                                    .setVersion("7"))));
                    final ProjectJSON cProjectJSON = new ProjectJSON()
                        .setProject("c")
                        .setPublisher("me")
                        .setVersion("7");
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        JSON.object(aProjectJSON::write).toString()).await();
                    fileSystem.setFileContentAsString("/qub/me/b/5/b.jar", "hello").await();
                    fileSystem.setFileContentAsString("/qub/me/b/5/project.json", JSON.object(bProjectJSON::write).toString()).await();
                    fileSystem.setFileContentAsString("/qub/me/c/7/c.jar", "hello").await();
                    fileSystem.setFileContentAsString("/qub/me/c/7/project.json", JSON.object(cProjectJSON::write).toString()).await();
                    try (final Console console = new Console())
                    {
                        console.setOutputByteWriteStream(output);
                        console.setErrorByteWriteStream(error);
                        console.setFileSystem(fileSystem);
                        console.setCurrentFolderPath(Path.parse("/"));
                        console.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = console.getCurrentFolder().await();
                        console.setProcessFactory(new FakeProcessFactory(console.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath(Iterable.create("/outputs", "/qub/me/b/5/b.jar", "/qub/me/c/7/c.jar"))
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath("/outputs;/qub/me/b/5/b.jar;/qub/me/c/7/c.jar")
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder("/outputs")
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject"))));

                        main(console);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/a@1..."
                            ),
                            Strings.getLines(output.asCharacterReadStream().getText().await()).skipLast());
                        test.assertEqual("", error.asCharacterReadStream().getText().await());

                        test.assertEqual(0, console.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Manifest file:",
                            "META-INF/MANIFEST.MF",
                            "",
                            "Files:",
                            "MyProject.class",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/a/1/a.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Files:",
                            "MyProject.java",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/a/1/a.sources.jar").await()));
                    test.assertEqual(
                        "{\"publisher\":\"me\",\"project\":\"a\",\"version\":\"1\",\"java\":{\"mainClass\":\"MyProject\",\"dependencies\":[{\"publisher\":\"me\",\"project\":\"b\",\"version\":\"5\"}]}}",
                        fileSystem.getFileContentAsString("/qub/me/a/1/project.json").await());
                    test.assertEqual(
                        Iterable.create(
                            "@echo OFF",
                            "java -classpath %~dp0me/a/1/a.jar;%~dp0me/b/5/b.jar;%~dp0me/c/7/c.jar MyProject %*"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/a.cmd").await()));
                });

                runner.test("with mainClass and shortcutName in project.json", (Test test) ->
                {
                    final InMemoryByteStream output = new InMemoryByteStream();
                    final InMemoryByteStream error = new InMemoryByteStream();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = new ProjectJSON();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(new ProjectJSONJava()
                        .setMainClass("MyProject")
                        .setShortcutName("foo"));
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        JSON.object(projectJSON::write).toString()).await();
                    try (final Console console = new Console())
                    {
                        console.setOutputByteWriteStream(output);
                        console.setErrorByteWriteStream(error);
                        console.setFileSystem(fileSystem);
                        console.setCurrentFolderPath(Path.parse("/"));
                        console.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = console.getCurrentFolder().await();
                        console.setProcessFactory(new FakeProcessFactory(console.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath("/outputs")
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath("/outputs")
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder("/outputs")
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject"))));

                        main(console);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/my-project@1..."
                            ),
                            Strings.getLines(output.asCharacterReadStream().getText().await()).skipLast());
                        test.assertEqual("", error.asCharacterReadStream().getText().await());

                        test.assertEqual(0, console.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Manifest file:",
                            "META-INF/MANIFEST.MF",
                            "",
                            "Files:",
                            "MyProject.class",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/1/my-project.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Files:",
                            "MyProject.java",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/1/my-project.sources.jar").await()));
                    test.assertEqual(
                        "{\"publisher\":\"me\",\"project\":\"my-project\",\"version\":\"1\",\"java\":{\"mainClass\":\"MyProject\",\"shortcutName\":\"foo\"}}",
                        fileSystem.getFileContentAsString("/qub/me/my-project/1/project.json").await());
                    test.assertFalse(fileSystem.fileExists("/qub/my-project.cmd").await());
                    test.assertEqual(
                        Iterable.create(
                            "@echo OFF",
                            "java -classpath %~dp0me/my-project/1/my-project.jar MyProject %*"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/foo.cmd").await()));
                });

                runner.test("with dependent and non-dependent published project", (Test test) ->
                {
                    final InMemoryByteStream output = new InMemoryByteStream();
                    final InMemoryByteStream error = new InMemoryByteStream();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/qub/me/other-project/10/project.json",
                        new ProjectJSON()
                            .setPublisher("me")
                            .setProject("other-project")
                            .setVersion("10")
                            .setJava(new ProjectJSONJava()
                                .setDependencies(Iterable.create(
                                    new Dependency()
                                        .setPublisher("me")
                                        .setProject("my-project")
                                        .setVersion("1"))))
                            .toString()).await();
                    fileSystem.setFileContentAsString("/qub/me/my-other-project/5/project.json",
                        new ProjectJSON()
                            .setPublisher("me")
                            .setProject("my-other-project")
                            .setVersion("5")
                            .setJava(new ProjectJSONJava()
                                .setDependencies(Iterable.create()))
                            .toString()).await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    fileSystem.setFileContentAsString("/project.json",
                        new ProjectJSON()
                            .setProject("my-project")
                            .setPublisher("me")
                            .setVersion("2")
                            .setJava(new ProjectJSONJava())
                            .toString()).await();
                    try (final Console console = new Console())
                    {
                        console.setOutputByteWriteStream(output);
                        console.setErrorByteWriteStream(error);
                        console.setFileSystem(fileSystem);
                        console.setCurrentFolderPath(Path.parse("/"));
                        console.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = console.getCurrentFolder().await();
                        console.setProcessFactory(new FakeProcessFactory(console.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath("/outputs")
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath("/outputs")
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(false)
                            .addTestJson(true)
                            .addOutputFolder("/outputs")
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("MyProject"))));

                        main(console);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/my-project@2...",
                                "The following projects should be updated to use me/my-project@2:",
                                "  me/other-project@10"
                            ),
                            Strings.getLines(output.asCharacterReadStream().getText().await()).skipLast());
                        test.assertEqual("", error.asCharacterReadStream().getText().await());

                        test.assertEqual(0, console.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Files:",
                            "MyProject.class",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/2/my-project.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Files:",
                            "MyProject.java",
                            ""),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/2/my-project.sources.jar").await()));
                    test.assertEqual(
                        new ProjectJSON()
                            .setPublisher("me")
                            .setProject("my-project")
                            .setVersion("2")
                            .setJava(new ProjectJSONJava())
                        .toString(),
                        fileSystem.getFileContentAsString("/qub/me/my-project/2/project.json").await());
                    test.assertFalse(fileSystem.fileExists("/qub/my-project.cmd").await());
                });
            });
        });
    }

    static void main(Console console)
    {
        final QubPack qubPack = new QubPack();
        qubPack.setJarCreator(new FakeJarCreator());

        main(console, qubPack);
    }

    static void main(Console console, QubPack qubPack)
    {
        final QubPublish qubPublish = new QubPublish();
        qubPublish.setQubPack(qubPack);

        qubPublish.main(console);
    }
}

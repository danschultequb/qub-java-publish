package qub;

public interface QubPublishTests
{
    static void test(TestRunner runner)
    {
        runner.testGroup(QubPublish.class, () ->
        {
            runner.testGroup("main(String[])", () ->
            {
                runner.test("with null", (Test test) ->
                {
                    test.assertThrows(new PreConditionFailure("args cannot be null."),
                        () -> QubPublish.main((String[])null));
                });
            });

            runner.testGroup("main(QubProcess)", () ->
            {
                runner.test("with null", (Test test) ->
                {
                    test.assertThrows(new PreConditionFailure("process cannot be null."),
                        () -> QubPublish.main((QubProcess)null));
                });

                runner.test("with \"-?\"", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    try (final QubProcess process = QubProcess.create("-?"))
                    {
                        process.setOutputWriteStream(output);
                        process.setErrorWriteStream(error);

                        QubPublish.main(process);

                        test.assertEqual(-1, process.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Usage: qub-publish [[--folder=]<folder-to-publish>] [--packjson] [--testjson] [--coverage[=<None|Sources|Tests|All>]] [--buildjson] [--warnings=<show|error|hide>] [--verbose] [--profiler] [--help]",
                            "  Used to published packaged source and compiled code to the qub folder.",
                            "  --folder: The folder to publish. Defaults to the current folder.",
                            "  --packjson: Whether or not to read and write a pack.json file. Defaults to true.",
                            "  --testjson: Whether or not to write the test results to a test.json file.",
                            "  --coverage(c): Whether or not to collect code coverage information while running tests.",
                            "  --buildjson: Whether or not to read and write a build.json file. Defaults to true.",
                            "  --warnings: How to handle build warnings. Can be either \"show\", \"error\", or \"hide\". Defaults to \"show\".",
                            "  --verbose(v): Whether or not to show verbose logs.",
                            "  --profiler: Whether or not this application should pause before it is run to allow a profiler to be attached.",
                            "  --help(?): Show the help message for this application."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                });

                runner.test("with no QUB_HOME specified", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = ProjectJSON.create();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(ProjectJSONJava.create());
                    fileSystem.setFileContentAsString("/project.json", projectJSON.toString()).await();
                    try (final QubProcess process = QubProcess.create())
                    {
                        process.setOutputWriteStream(output);
                        process.setErrorWriteStream(error);
                        process.setFileSystem(fileSystem);
                        process.setCurrentFolderPath(Path.parse("/"));
                        process.setEnvironmentVariables(new EnvironmentVariables());

                        QubPublish.main(process);

                        test.assertEqual(1, process.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "ERROR: Can't publish without a QUB_HOME environment variable."
                        ),
                        Strings.getLines(output.getText().await()).skipLast());
                    test.assertEqual("", error.getText().await());
                });

                runner.test("with failed QubPack", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = ProjectJSON.create();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(ProjectJSONJava.create());
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        projectJSON.toString()).await();
                    try (final QubProcess process = QubProcess.create())
                    {
                        process.setOutputWriteStream(output);
                        process.setErrorWriteStream(error);
                        process.setFileSystem(fileSystem);
                        process.setCurrentFolderPath(Path.parse("/"));
                        process.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = process.getCurrentFolder().await();
                        process.setJVMClasspath(currentFolder.getFolder("outputs").await().toString());
                        process.setProcessFactory(new FakeProcessFactory(process.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath(currentFolder.getFolder("outputs").await().toString())
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath(currentFolder.getFolder("outputs").await().toString())
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject"))
                                .setFunction(1)));

                        QubPublish.main(process);
                        
                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                ""
                            ),
                            Strings.getLines(output.getText().await()).skipLast());
                        test.assertEqual("", error.getText().await());
                    
                        test.assertEqual(1, process.getExitCode());
                    }
                });

                runner.test("with already existing version folder", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/me/my-project/versions/1/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = ProjectJSON.create();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(ProjectJSONJava.create());
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        projectJSON.toString()).await();
                    try (final QubProcess process = QubProcess.create())
                    {
                        process.setOutputWriteStream(output);
                        process.setErrorWriteStream(error);
                        process.setFileSystem(fileSystem);
                        process.setCurrentFolderPath(Path.parse("/"));
                        process.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = process.getCurrentFolder().await();
                        process.setJVMClasspath(currentFolder.getFolder("outputs").await().toString());
                        process.setProcessFactory(new FakeProcessFactory(process.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath(currentFolder.getFolder("outputs").await().toString())
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath(currentFolder.getFolder("outputs").await().toString())
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject")))
                            .add(new FakeJarProcessRun()
                                .setWorkingFolder(currentFolder.getFolder("sources").await())
                                .addCreate()
                                .addJarFile("my-project.sources.jar")
                                .addContentFilePath("MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeJarProcessRun()
                                .setWorkingFolder(currentFolder.getFolder("outputs").await())
                                .addCreate()
                                .addJarFile("my-project.jar")
                                .addContentFilePath("MyProject.class")
                                .setFunctionAutomatically()));

                        QubPublish.main(process);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "ERROR: This package (me/my-project:1) can't be published because a package with that signature already exists."
                            ),
                            Strings.getLines(output.getText().await()).skipLast());
                        test.assertEqual("", error.getText().await());

                        test.assertEqual(1, process.getExitCode());
                    }
                    test.assertFalse(fileSystem.fileExists("/qub/me/my-project/versions/1/my-project.jar").await());
                    test.assertFalse(fileSystem.fileExists("/qub/me/my-project/versions/1/my-project.sources.jar").await());
                    test.assertFalse(fileSystem.fileExists("/qub/me/my-project/versions/1/project.json").await());
                    test.assertFalse(fileSystem.fileExists("/qub/my-project.cmd").await());
                });

                runner.test("with simple success scenario", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = ProjectJSON.create();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(ProjectJSONJava.create());
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        projectJSON.toString()).await();
                    try (final QubProcess process = QubProcess.create())
                    {
                        process.setOutputWriteStream(output);
                        process.setErrorWriteStream(error);
                        process.setFileSystem(fileSystem);
                        process.setCurrentFolderPath(Path.parse("/"));
                        process.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = process.getCurrentFolder().await();
                        process.setJVMClasspath(currentFolder.getFolder("outputs").await().toString());
                        process.setProcessFactory(new FakeProcessFactory(process.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath(currentFolder.getFolder("outputs").await().toString())
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath(currentFolder.getFolder("outputs").await().toString())
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject")))
                            .add(new FakeJarProcessRun()
                                .setWorkingFolder(currentFolder.getFolder("sources").await())
                                .addCreate()
                                .addJarFile("my-project.sources.jar")
                                .addContentFilePath("MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeJarProcessRun()
                                .setWorkingFolder(currentFolder.getFolder("outputs").await())
                                .addCreate()
                                .addJarFile("my-project.jar")
                                .addContentFilePath("MyProject.class")
                                .setFunctionAutomatically()));

                        QubPublish.main(process);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/my-project@1..."
                            ),
                            Strings.getLines(output.getText().await()).skipLast());
                        test.assertEqual("", error.getText().await());

                        test.assertEqual(0, process.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "MyProject.class"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/versions/1/my-project.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "MyProject.java"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/versions/1/my-project.sources.jar").await()));
                    test.assertEqual(
                        ProjectJSON.create()
                            .setProject("my-project")
                            .setPublisher("me")
                            .setVersion("1")
                            .setJava(ProjectJSONJava.create())
                            .toString(),
                        fileSystem.getFileContentAsString("/qub/me/my-project/versions/1/project.json").await());
                    test.assertFalse(fileSystem.fileExists("/qub/my-project.cmd").await());
                });

                runner.test("with mainClass in project.json", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = ProjectJSON.create()
                        .setProject("my-project")
                        .setPublisher("me")
                        .setVersion("1")
                        .setJava(ProjectJSONJava.create()
                            .setMainClass("MyProject"));
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        projectJSON.toString()).await();
                    try (final QubProcess process = QubProcess.create())
                    {
                        process.setOutputWriteStream(output);
                        process.setErrorWriteStream(error);
                        process.setFileSystem(fileSystem);
                        process.setCurrentFolderPath(Path.parse("/"));
                        process.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = process.getCurrentFolder().await();
                        process.setJVMClasspath(currentFolder.getFolder("outputs").await().toString());
                        process.setProcessFactory(new FakeProcessFactory(process.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath(currentFolder.getFolder("outputs").await().toString())
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath(currentFolder.getFolder("outputs").await().toString())
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject")))
                            .add(new FakeJarProcessRun()
                                .setWorkingFolder(currentFolder.getFolder("sources").await())
                                .addCreate()
                                .addJarFile("my-project.sources.jar")
                                .addContentFilePath("MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeJarProcessRun()
                                .setWorkingFolder(currentFolder.getFolder("outputs").await())
                                .addCreate()
                                .addJarFile("my-project.jar")
                                .addManifestFile("/outputs/META-INF/MANIFEST.MF")
                                .addContentFilePath("MyProject.class")
                                .setFunctionAutomatically()));

                        QubPublish.main(process);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/my-project@1..."
                            ),
                            Strings.getLines(output.getText().await()).skipLast());
                        test.assertEqual("", error.getText().await());

                        test.assertEqual(0, process.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Manifest File:",
                            "/outputs/META-INF/MANIFEST.MF",
                            "",
                            "Content Files:",
                            "MyProject.class"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/versions/1/my-project.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "MyProject.java"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/versions/1/my-project.sources.jar").await()));
                    test.assertEqual(
                        ProjectJSON.create()
                            .setProject("my-project")
                            .setPublisher("me")
                            .setVersion("1")
                            .setJava(ProjectJSONJava.create()
                                .setMainClass("MyProject"))
                            .toString(),
                        fileSystem.getFileContentAsString("/qub/me/my-project/versions/1/project.json").await());
                    test.assertEqual(
                        Iterable.create(
                            "@echo OFF",
                            "java -classpath %~dp0me/my-project/versions/1/my-project.jar MyProject %*"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/my-project.cmd").await()));
                });

                runner.test("with mainClass and dependencies in project.json", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = ProjectJSON.create();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(ProjectJSONJava.create()
                        .setMainClass("MyProject")
                        .setDependencies(Iterable.create(
                            new ProjectSignature("me", "my-other-project", "5"),
                            new ProjectSignature("you", "stuff", "7.3.1"))));
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        projectJSON.toString()).await();
                    fileSystem.setFileContentAsString("/qub/me/my-other-project/versions/5/my-other-project.jar", "hello").await();
                    fileSystem.setFileContentAsString("/qub/you/stuff/versions/7.3.1/stuff.jar", "hello2").await();
                    try (final QubProcess process = QubProcess.create())
                    {
                        process.setOutputWriteStream(output);
                        process.setErrorWriteStream(error);
                        process.setFileSystem(fileSystem);
                        process.setCurrentFolderPath(Path.parse("/"));
                        process.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = process.getCurrentFolder().await();
                        process.setJVMClasspath(currentFolder.getFolder("outputs").await().toString());
                        process.setProcessFactory(new FakeProcessFactory(process.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath(Iterable.create(
                                    "/outputs/",
                                    "/qub/me/my-other-project/versions/5/my-other-project.jar",
                                    "/qub/you/stuff/versions/7.3.1/stuff.jar"))
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath("/outputs/;/qub/you/stuff/versions/7.3.1/stuff.jar;/qub/me/my-other-project/versions/5/my-other-project.jar")
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject")))
                            .add(new FakeJarProcessRun()
                                .setWorkingFolder(currentFolder.getFolder("sources").await())
                                .addCreate()
                                .addJarFile("my-project.sources.jar")
                                .addContentFilePath("MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeJarProcessRun()
                                .setWorkingFolder(currentFolder.getFolder("outputs").await())
                                .addCreate()
                                .addJarFile("my-project.jar")
                                .addManifestFile("/outputs/META-INF/MANIFEST.MF")
                                .addContentFilePath("MyProject.class")
                                .setFunctionAutomatically()));

                        QubPublish.main(process);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/my-project@1..."
                            ),
                            Strings.getLines(output.getText().await()).skipLast());
                        test.assertEqual("", error.getText().await());

                        test.assertEqual(0, process.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Manifest File:",
                            "/outputs/META-INF/MANIFEST.MF",
                            "",
                            "Content Files:",
                            "MyProject.class"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/versions/1/my-project.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "MyProject.java"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/versions/1/my-project.sources.jar").await()));
                    test.assertEqual(
                        ProjectJSON.create()
                            .setProject("my-project")
                            .setPublisher("me")
                            .setVersion("1")
                            .setJava(ProjectJSONJava.create()
                                .setMainClass("MyProject")
                                .setDependencies(Iterable.create(
                                    new ProjectSignature("me", "my-other-project", "5"),
                                    new ProjectSignature("you", "stuff", "7.3.1"))))
                            .toString(),
                        fileSystem.getFileContentAsString("/qub/me/my-project/versions/1/project.json").await());
                    test.assertEqual(
                        Iterable.create(
                            "@echo OFF",
                            "java -classpath %~dp0me/my-project/versions/1/my-project.jar;%~dp0you/stuff/versions/7.3.1/stuff.jar;%~dp0me/my-other-project/versions/5/my-other-project.jar MyProject %*"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/my-project.cmd").await()));
                });

                runner.test("with mainClass and transitive dependencies in project.json", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON aProjectJSON = ProjectJSON.create()
                        .setProject("a")
                        .setPublisher("me")
                        .setVersion("1")
                        .setJava(ProjectJSONJava.create()
                            .setMainClass("MyProject")
                            .setDependencies(Iterable.create(
                                new ProjectSignature("me", "b", "5"))));
                    final ProjectJSON bProjectJSON = ProjectJSON.create()
                        .setProject("b")
                        .setPublisher("me")
                        .setVersion("5")
                        .setJava(ProjectJSONJava.create()
                            .setDependencies(Iterable.create(
                                new ProjectSignature("me", "c", "7"))));
                    final ProjectJSON cProjectJSON = ProjectJSON.create()
                        .setProject("c")
                        .setPublisher("me")
                        .setVersion("7");
                    fileSystem.setFileContentAsString("/project.json", aProjectJSON.toString()).await();
                    fileSystem.setFileContentAsString("/qub/me/b/versions/5/b.jar", "hello").await();
                    fileSystem.setFileContentAsString("/qub/me/b/versions/5/project.json", bProjectJSON.toString()).await();
                    fileSystem.setFileContentAsString("/qub/me/c/versions/7/c.jar", "hello").await();
                    fileSystem.setFileContentAsString("/qub/me/c/versions/7/project.json", cProjectJSON.toString()).await();
                    try (final QubProcess process = QubProcess.create())
                    {
                        process.setOutputWriteStream(output);
                        process.setErrorWriteStream(error);
                        process.setFileSystem(fileSystem);
                        process.setCurrentFolderPath(Path.parse("/"));
                        process.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = process.getCurrentFolder().await();
                        process.setJVMClasspath(currentFolder.getFolder("outputs").await().toString());
                        process.setProcessFactory(new FakeProcessFactory(process.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath(Iterable.create("/outputs/", "/qub/me/b/versions/5/b.jar", "/qub/me/c/versions/7/c.jar"))
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath("/outputs/;/qub/me/b/versions/5/b.jar;/qub/me/c/versions/7/c.jar")
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject")))
                            .add(new FakeJarProcessRun()
                                .setWorkingFolder(currentFolder.getFolder("sources").await())
                                .addCreate()
                                .addJarFile("a.sources.jar")
                                .addContentFilePath("MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeJarProcessRun()
                                .setWorkingFolder(currentFolder.getFolder("outputs").await())
                                .addCreate()
                                .addJarFile("a.jar")
                                .addManifestFile("/outputs/META-INF/MANIFEST.MF")
                                .addContentFilePath("MyProject.class")
                                .setFunctionAutomatically()));

                        QubPublish.main(process);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/a@1..."
                            ),
                            Strings.getLines(output.getText().await()).skipLast());
                        test.assertEqual("", error.getText().await());

                        test.assertEqual(0, process.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Manifest File:",
                            "/outputs/META-INF/MANIFEST.MF",
                            "",
                            "Content Files:",
                            "MyProject.class"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/a/versions/1/a.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "MyProject.java"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/a/versions/1/a.sources.jar").await()));
                    test.assertEqual(
                        ProjectJSON.create()
                            .setProject("a")
                            .setPublisher("me")
                            .setVersion("1")
                            .setJava(ProjectJSONJava.create()
                                .setMainClass("MyProject")
                                .setDependencies(Iterable.create(
                                    new ProjectSignature("me", "b", "5"))))
                            .toString(),
                        fileSystem.getFileContentAsString("/qub/me/a/versions/1/project.json").await());
                    test.assertEqual(
                        Iterable.create(
                            "@echo OFF",
                            "java -classpath %~dp0me/a/versions/1/a.jar;%~dp0me/b/versions/5/b.jar;%~dp0me/c/versions/7/c.jar MyProject %*"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/a.cmd").await()));
                });

                runner.test("with mainClass and shortcutName in project.json", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    final ProjectJSON projectJSON = ProjectJSON.create();
                    projectJSON.setProject("my-project");
                    projectJSON.setPublisher("me");
                    projectJSON.setVersion("1");
                    projectJSON.setJava(ProjectJSONJava.create()
                        .setMainClass("MyProject")
                        .setShortcutName("foo"));
                    fileSystem.setFileContentAsString(
                        "/project.json",
                        projectJSON.toString()).await();
                    try (final QubProcess process = QubProcess.create())
                    {
                        process.setOutputWriteStream(output);
                        process.setErrorWriteStream(error);
                        process.setFileSystem(fileSystem);
                        process.setCurrentFolderPath(Path.parse("/"));
                        process.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = process.getCurrentFolder().await();
                        process.setJVMClasspath(currentFolder.getFolder("outputs").await().toString());
                        process.setProcessFactory(new FakeProcessFactory(process.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath(currentFolder.getFolder("outputs").await().toString())
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeConsoleTestRunnerProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addClasspath(currentFolder.getFolder("outputs").await().toString())
                                .addConsoleTestRunnerFullClassName()
                                .addProfiler(false)
                                .addVerbose(false)
                                .addTestJson(true)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addCoverage(Coverage.None)
                                .addFullClassNamesToTest(Iterable.create("MyProject")))
                            .add(new FakeJarProcessRun()
                                .setWorkingFolder(currentFolder.getFolder("sources").await())
                                .addCreate()
                                .addJarFile("my-project.sources.jar")
                                .addContentFilePath("MyProject.java")
                                .setFunctionAutomatically())
                            .add(new FakeJarProcessRun()
                                .setWorkingFolder(currentFolder.getFolder("outputs").await())
                                .addCreate()
                                .addJarFile("my-project.jar")
                                .addManifestFile("/outputs/META-INF/MANIFEST.MF")
                                .addContentFilePath("MyProject.class")
                                .setFunctionAutomatically()));

                        QubPublish.main(process);

                        test.assertEqual(
                            Iterable.create(
                                "Compiling 1 file...",
                                "Running tests...",
                                "",
                                "Creating sources jar file...",
                                "Creating compiled sources jar file...",
                                "Publishing me/my-project@1..."
                            ),
                            Strings.getLines(output.getText().await()).skipLast());
                        test.assertEqual("", error.getText().await());

                        test.assertEqual(0, process.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Manifest File:",
                            "/outputs/META-INF/MANIFEST.MF",
                            "",
                            "Content Files:",
                            "MyProject.class"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/versions/1/my-project.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "MyProject.java"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/versions/1/my-project.sources.jar").await()));
                    test.assertEqual(
                        ProjectJSON.create()
                            .setProject("my-project")
                            .setPublisher("me")
                            .setVersion("1")
                            .setJava(ProjectJSONJava.create()
                                .setMainClass("MyProject")
                                .setShortcutName("foo"))
                            .toString(),
                        fileSystem.getFileContentAsString("/qub/me/my-project/versions/1/project.json").await());
                    test.assertFalse(fileSystem.fileExists("/qub/my-project.cmd").await());
                    test.assertEqual(
                        Iterable.create(
                            "@echo OFF",
                            "java -classpath %~dp0me/my-project/versions/1/my-project.jar MyProject %*"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/foo.cmd").await()));
                });

                runner.test("with dependent and non-dependent published project", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = new InMemoryFileSystem(test.getClock());
                    fileSystem.createRoot("/").await();
                    fileSystem.createFolder("/qub/").await();
                    fileSystem.setFileContentAsString("/qub/me/other-project/versions/10/project.json",
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("other-project")
                            .setVersion("10")
                            .setJava(ProjectJSONJava.create()
                                .setDependencies(Iterable.create(
                                    new ProjectSignature("me", "my-project", "1"))))
                            .toString()).await();
                    fileSystem.setFileContentAsString("/qub/me/my-other-project/versions/5/project.json",
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("my-other-project")
                            .setVersion("5")
                            .setJava(ProjectJSONJava.create()
                                .setDependencies(Iterable.create()))
                            .toString()).await();
                    fileSystem.setFileContentAsString("/sources/MyProject.java", "hello").await();
                    fileSystem.setFileContentAsString("/project.json",
                        ProjectJSON.create()
                            .setProject("my-project")
                            .setPublisher("me")
                            .setVersion("2")
                            .setJava(ProjectJSONJava.create())
                            .toString()).await();
                    try (final QubProcess process = QubProcess.create())
                    {
                        process.setOutputWriteStream(output);
                        process.setErrorWriteStream(error);
                        process.setFileSystem(fileSystem);
                        process.setCurrentFolderPath(Path.parse("/"));
                        process.setEnvironmentVariables(new EnvironmentVariables()
                            .set("QUB_HOME", "/qub/"));

                        final Folder currentFolder = process.getCurrentFolder().await();
                        process.setJVMClasspath(currentFolder.getFolder("outputs").await().toString());
                        process.setProcessFactory(new FakeProcessFactory(process.getParallelAsyncRunner(), currentFolder)
                            .add(new FakeJavacProcessRun()
                                .setWorkingFolder(currentFolder)
                                .addOutputFolder(currentFolder.getFolder("outputs").await())
                                .addXlintUnchecked()
                                .addXlintDeprecation()
                                .addClasspath(currentFolder.getFolder("outputs").await().toString())
                                .addSourceFile("sources/MyProject.java")
                                .setFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath(currentFolder.getFolder("outputs").await().toString())
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(false)
                            .addTestJson(true)
                            .addOutputFolder(currentFolder.getFolder("outputs").await().toString())
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("MyProject")))
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(currentFolder.getFolder("sources").await())
                            .addCreate()
                            .addJarFile("my-project.sources.jar")
                            .addContentFilePath("MyProject.java")
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(currentFolder.getFolder("outputs").await())
                            .addCreate()
                            .addJarFile("my-project.jar")
                            .addContentFilePath("MyProject.class")
                            .setFunctionAutomatically()));

                        QubPublish.main(process);

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
                            Strings.getLines(output.getText().await()).skipLast());
                        test.assertEqual("", error.getText().await());

                        test.assertEqual(0, process.getExitCode());
                    }
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "MyProject.class"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/versions/2/my-project.jar").await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "MyProject.java"),
                        Strings.getLines(fileSystem.getFileContentAsString("/qub/me/my-project/versions/2/my-project.sources.jar").await()));
                    test.assertEqual(
                        ProjectJSON.create()
                            .setProject("my-project")
                            .setPublisher("me")
                            .setVersion("2")
                            .setJava(ProjectJSONJava.create())
                        .toString(),
                        fileSystem.getFileContentAsString("/qub/me/my-project/versions/2/project.json").await());
                    test.assertFalse(fileSystem.fileExists("/qub/my-project.cmd").await());
                });
            });
        });
    }
}

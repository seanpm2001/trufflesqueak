package de.hpi.swa.trufflesqueak.test;

import static org.junit.Assert.assertNotEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import de.hpi.swa.trufflesqueak.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.BaseSqueakObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.ObjectLayouts.SPECIAL_OBJECT_INDEX;
import de.hpi.swa.trufflesqueak.model.ObjectLayouts.TEST_RESULT;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.nodes.InvokeNode;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SqueakInImageTest extends AbstractSqueakTestCase {
    private static final String IMAGE_PATH = getImagesPathName() + File.separator + "test.image";
    private static Object smalltalkDictionary;
    private static Object smalltalkAssociation;
    private static Object evaluateSymbol;
    private static Object compilerSymbol;

    private static final class TEST_TYPE {
        private static final String PASSING = "Passing"; // should pass
        private static final String FAILING = "Failing"; // some/all test selectors fail/error
        private static final String BROKEN = "Broken"; // throws a Java exceptions
        private static final String INCONSISTENT = "Inconsistent"; // runs sometimes (only non/virtualized, because of side effects, ...)
        private static final String NOT_TERMINATING = "Not Terminating"; // does not terminate
        private static final String BROKEN_IN_SQUEAK = "Broken in Squeak"; // not working in Squeak
        private static final String REQUIRES_STARTUP = "Requires Startup"; // requires the image to be entirely started (e.g. load changes, initialize display, ...)
        private static final String IGNORE = "Ignored"; // unable to run (e.g. OOM, ...)
    }

    private static final Object[] squeakTests = new Object[]{"AddPrefixNamePolicyTest", TEST_TYPE.PASSING,
                    "AliasTest", TEST_TYPE.PASSING,
                    "AllNamePolicyTest", TEST_TYPE.PASSING,
                    "AllocationTest", TEST_TYPE.IGNORE,
                    "ArbitraryObjectSocketTestCase", TEST_TYPE.FAILING,
                    "ArrayLiteralTest", TEST_TYPE.FAILING,
                    "ArrayTest", TEST_TYPE.FAILING,
                    "Ascii85ConverterTest", TEST_TYPE.FAILING,
                    "AssociationTest", TEST_TYPE.PASSING,
                    "BagTest", TEST_TYPE.PASSING,
                    "BalloonFontTest", TEST_TYPE.PASSING,
                    "Base64MimeConverterTest", TEST_TYPE.PASSING,
                    "BasicBehaviorClassMetaclassTest", TEST_TYPE.PASSING,
                    "BasicTypeTest", TEST_TYPE.PASSING,
                    "BecomeTest", TEST_TYPE.FAILING,
                    "BehaviorTest", TEST_TYPE.FAILING,
                    "BindingPolicyTest", TEST_TYPE.PASSING,
                    "BitBltClipBugs", TEST_TYPE.FAILING,
                    "BitBltTest", TEST_TYPE.FAILING,
                    "BitmapBugz", TEST_TYPE.PASSING,
                    "BitmapStreamTests", TEST_TYPE.IGNORE, // OOM error
                    "BitSetTest", TEST_TYPE.PASSING,
                    "BlockClosureTest", TEST_TYPE.INCONSISTENT,
                    "BlockLocalTemporariesRemovalTest", TEST_TYPE.PASSING,
                    "BMPReadWriterTest", TEST_TYPE.FAILING,
                    "BooleanTest", TEST_TYPE.PASSING,
                    "BrowserHierarchicalListTest", TEST_TYPE.PASSING,
                    "BrowserTest", TEST_TYPE.FAILING,
                    "BrowseTest", TEST_TYPE.BROKEN,
                    "ByteArrayTest", TEST_TYPE.FAILING,
                    "BytecodeDecodingTests", TEST_TYPE.FAILING,
                    "ByteEncoderTest", TEST_TYPE.FAILING,
                    "CategorizerTest", TEST_TYPE.FAILING,
                    "ChainedSortFunctionTest", TEST_TYPE.PASSING,
                    "ChangeHooksTest", TEST_TYPE.REQUIRES_STARTUP,
                    "ChangeSetClassChangesTest", TEST_TYPE.REQUIRES_STARTUP,
                    "CharacterScannerTest", TEST_TYPE.PASSING,
                    "CharacterSetComplementTest", TEST_TYPE.PASSING,
                    "CharacterSetTest", TEST_TYPE.PASSING,
                    "CharacterTest", TEST_TYPE.PASSING,
                    "CircleMorphBugs", TEST_TYPE.PASSING,
                    "CircleMorphTest", TEST_TYPE.PASSING,
                    "ClassAPIHelpBuilderTest", TEST_TYPE.PASSING,
                    "ClassBindingTest", TEST_TYPE.PASSING,
                    "ClassBuilderTest", TEST_TYPE.FAILING,
                    "ClassDescriptionTest", TEST_TYPE.FAILING,
                    "ClassFactoryForTestCaseTest", TEST_TYPE.INCONSISTENT,
                    "ClassRemovalTest", TEST_TYPE.FAILING,
                    "ClassRenameFixTest", TEST_TYPE.REQUIRES_STARTUP,
                    "ClassTest", TEST_TYPE.FAILING,
                    "ClassTestCase", TEST_TYPE.NOT_TERMINATING,
                    "ClassTraitTest", TEST_TYPE.NOT_TERMINATING,
                    "ClassVarScopeTest", TEST_TYPE.BROKEN,
                    "ClipboardTest", TEST_TYPE.PASSING,
                    "ClosureCompilerTest", TEST_TYPE.BROKEN,
                    "ClosureTests", TEST_TYPE.FAILING,
                    "CogVMBaseImageTests", TEST_TYPE.FAILING,
                    "CollectionTest", TEST_TYPE.PASSING,
                    "ColorTest", TEST_TYPE.FAILING,
                    "CompiledMethodComparisonTest", TEST_TYPE.BROKEN,
                    "CompiledMethodTest", TEST_TYPE.FAILING,
                    "CompiledMethodTrailerTest", TEST_TYPE.INCONSISTENT,
                    "CompilerExceptionsTest", TEST_TYPE.INCONSISTENT,
                    "CompilerNotifyingTest", TEST_TYPE.FAILING,
                    "CompilerSyntaxErrorNotifyingTest", TEST_TYPE.FAILING,
                    "CompilerTest", TEST_TYPE.PASSING,
                    "ComplexTest", TEST_TYPE.FAILING,
                    "ContextCompilationTest", TEST_TYPE.PASSING,
                    "DataStreamTest", TEST_TYPE.FAILING,
                    "DateAndTimeEpochTest", TEST_TYPE.PASSING,
                    "DateAndTimeLeapTest", TEST_TYPE.FAILING,
                    "DateAndTimeTest", TEST_TYPE.NOT_TERMINATING,
                    "DateTest", TEST_TYPE.PASSING,
                    "DebuggerExtensionsTest", TEST_TYPE.FAILING,
                    "DebuggerUnwindBug", TEST_TYPE.NOT_TERMINATING,
                    "DecompilerTests", TEST_TYPE.NOT_TERMINATING,
                    "DelayTest", TEST_TYPE.NOT_TERMINATING,
                    "DependencyBrowserTest", TEST_TYPE.FAILING,
                    "DependentsArrayTest", TEST_TYPE.FAILING,
                    "DictionaryTest", TEST_TYPE.FAILING,
                    "DosFileDirectoryTests", TEST_TYPE.PASSING,
                    "DoubleByteArrayTest", TEST_TYPE.FAILING,
                    "DoubleWordArrayTest", TEST_TYPE.FAILING,
                    "DurationTest", TEST_TYPE.FAILING,
                    "EnvironmentTest", TEST_TYPE.FAILING,
                    "EPSCanvasTest", TEST_TYPE.NOT_TERMINATING,
                    "EtoysStringExtensionTest", TEST_TYPE.PASSING,
                    "EventManagerTest", TEST_TYPE.PASSING,
                    "ExceptionTests", TEST_TYPE.NOT_TERMINATING,
                    "ExpandedSourceFileArrayTest", TEST_TYPE.PASSING,
                    "ExplicitNamePolicyTest", TEST_TYPE.PASSING,
                    "ExtendedNumberParserTest", TEST_TYPE.FAILING,
                    "FalseTest", TEST_TYPE.FAILING,
                    "FileContentsBrowserTest", TEST_TYPE.FAILING,
                    "FileDirectoryTest", TEST_TYPE.BROKEN,
                    "FileList2ModalDialogsTest", TEST_TYPE.FAILING,
                    "FileListTest", TEST_TYPE.FAILING, // needs 'FileDirectory startUp'
                    "FileStreamTest", TEST_TYPE.BROKEN,
                    "FileUrlTest", TEST_TYPE.PASSING,
                    "FlapTabTests", TEST_TYPE.NOT_TERMINATING,
                    "FloatArrayTest", TEST_TYPE.FAILING,
                    "FloatCollectionTest", TEST_TYPE.PASSING,
                    "FloatTest", TEST_TYPE.FAILING,
                    "FontTest", TEST_TYPE.FAILING,
                    "FormCanvasTest", TEST_TYPE.FAILING,
                    "FormTest", TEST_TYPE.FAILING,
                    "FractionTest", TEST_TYPE.FAILING,
                    "GeneratorTest", TEST_TYPE.BROKEN,
                    "GenericUrlTest", TEST_TYPE.PASSING,
                    "GlobalTest", TEST_TYPE.PASSING,
                    "GradientFillStyleTest", TEST_TYPE.PASSING,
                    "HandBugs", TEST_TYPE.INCONSISTENT,
                    "HashAndEqualsTestCase", TEST_TYPE.PASSING,
                    "HashedCollectionTest", TEST_TYPE.PASSING,
                    "HashTesterTest", TEST_TYPE.PASSING,
                    "HeapTest", TEST_TYPE.NOT_TERMINATING,
                    "HelpBrowserTest", TEST_TYPE.NOT_TERMINATING,
                    "HelpIconsTest", TEST_TYPE.PASSING,
                    "HelpTopicListItemWrapperTest", TEST_TYPE.PASSING,
                    "HelpTopicTest", TEST_TYPE.PASSING,
                    "HexTest", TEST_TYPE.PASSING,
                    "HierarchicalUrlTest", TEST_TYPE.PASSING,
                    "HierarchyBrowserTest", TEST_TYPE.PASSING,
                    "HtmlReadWriterTest", TEST_TYPE.PASSING,
                    "HttpUrlTest", TEST_TYPE.PASSING,
                    "IdentityBagTest", TEST_TYPE.PASSING,
                    "InstallerTest", TEST_TYPE.NOT_TERMINATING,
                    "InstallerUrlTest", TEST_TYPE.PASSING,
                    "InstructionClientTest", TEST_TYPE.PASSING,
                    "InstructionPrinterTest", TEST_TYPE.PASSING,
                    "InstVarRefLocatorTest", TEST_TYPE.PASSING,
                    "IntegerArrayTest", TEST_TYPE.FAILING,
                    "IntegerDigitLogicTest", TEST_TYPE.FAILING,
                    "IntegerTest", TEST_TYPE.NOT_TERMINATING,
                    "IntervalTest", TEST_TYPE.FAILING,
                    "IslandVMTweaksTestCase", TEST_TYPE.FAILING,
                    "JPEGReadWriter2Test", TEST_TYPE.FAILING,
                    "KeyedSetTest", TEST_TYPE.PASSING,
                    "LangEnvBugs", TEST_TYPE.BROKEN,
                    "LargeNegativeIntegerTest", TEST_TYPE.FAILING,
                    "LargePositiveIntegerTest", TEST_TYPE.FAILING,
                    "LayoutFrameTest", TEST_TYPE.FAILING,
                    "LinkedListTest", TEST_TYPE.PASSING,
                    "LocaleTest", TEST_TYPE.INCONSISTENT,
                    "MacFileDirectoryTest", TEST_TYPE.PASSING,
                    "MailAddressParserTest", TEST_TYPE.INCONSISTENT,
                    "MailDateAndTimeTest", TEST_TYPE.PASSING,
                    "MailMessageTest", TEST_TYPE.FAILING,
                    "MatrixTest", TEST_TYPE.PASSING,
                    "MCAncestryTest", TEST_TYPE.NOT_TERMINATING,
                    "MCChangeNotificationTest", TEST_TYPE.NOT_TERMINATING,
                    "MCClassDefinitionTest", TEST_TYPE.NOT_TERMINATING,
                    "MCDependencySorterTest", TEST_TYPE.PASSING,
                    "MCDictionaryRepositoryTest", TEST_TYPE.NOT_TERMINATING,
                    "MCDirectoryRepositoryTest", TEST_TYPE.NOT_TERMINATING,
                    "MCEnvironmentLoadTest", TEST_TYPE.NOT_TERMINATING,
                    "MCFileInTest", TEST_TYPE.NOT_TERMINATING,
                    "MCInitializationTest", TEST_TYPE.NOT_TERMINATING,
                    "MCMcmUpdaterTest", TEST_TYPE.PASSING,
                    "MCMczInstallerTest", TEST_TYPE.NOT_TERMINATING,
                    "MCMergingTest", TEST_TYPE.IGNORE,
                    "MCMethodDefinitionTest", TEST_TYPE.IGNORE,
                    "MCOrganizationTest", TEST_TYPE.IGNORE,
                    "MCPackageTest", TEST_TYPE.IGNORE,
                    "MCPatchTest", TEST_TYPE.IGNORE,
                    "MCPTest", TEST_TYPE.PASSING,
                    "MCRepositoryTest", TEST_TYPE.IGNORE,
                    "MCScannerTest", TEST_TYPE.IGNORE,
                    "MCSerializationTest", TEST_TYPE.IGNORE,
                    "MCSnapshotBrowserTest", TEST_TYPE.IGNORE,
                    "MCSnapshotTest", TEST_TYPE.IGNORE,
                    "MCSortingTest", TEST_TYPE.PASSING,
                    "MCStReaderTest", TEST_TYPE.IGNORE,
                    "MCStWriterTest", TEST_TYPE.IGNORE,
                    "MCVersionNameTest", TEST_TYPE.IGNORE,
                    "MCVersionTest", TEST_TYPE.IGNORE,
                    "MCWorkingCopyRenameTest", TEST_TYPE.IGNORE,
                    "MCWorkingCopyTest", TEST_TYPE.IGNORE,
                    "MessageNamesTest", TEST_TYPE.BROKEN,
                    "MessageSendTest", TEST_TYPE.PASSING,
                    "MessageSetTest", TEST_TYPE.BROKEN,
                    "MessageTraceTest", TEST_TYPE.BROKEN,
                    "MethodContextTest", TEST_TYPE.NOT_TERMINATING,
                    "MethodHighlightingTests", TEST_TYPE.PASSING,
                    "MethodPragmaTest", TEST_TYPE.BROKEN,
                    "MethodPropertiesTest", TEST_TYPE.BROKEN,
                    "MethodReferenceTest", TEST_TYPE.FAILING,
                    "MIMEDocumentTest", TEST_TYPE.PASSING,
                    "MirrorPrimitiveTests", TEST_TYPE.FAILING,
                    "MonitorTest", TEST_TYPE.NOT_TERMINATING,
                    "MonthTest", TEST_TYPE.PASSING,
                    "MorphBugs", TEST_TYPE.PASSING,
                    "MorphicEventDispatcherTests", TEST_TYPE.PASSING,
                    "MorphicEventFilterTests", TEST_TYPE.PASSING,
                    "MorphicEventTests", TEST_TYPE.INCONSISTENT,
                    "MorphicExtrasSymbolExtensionsTest", TEST_TYPE.PASSING,
                    "MorphicToolBuilderTests", TEST_TYPE.INCONSISTENT,
                    "MorphicUIManagerTest", TEST_TYPE.BROKEN,
                    "MorphTest", TEST_TYPE.PASSING,
                    "MultiByteFileStreamTest", TEST_TYPE.BROKEN,
                    "MVCToolBuilderTests", TEST_TYPE.NOT_TERMINATING,
                    "NamePolicyTest", TEST_TYPE.PASSING,
                    "NumberParsingTest", TEST_TYPE.FAILING,
                    "NumberTest", TEST_TYPE.FAILING,
                    "ObjectFinalizerTests", TEST_TYPE.FAILING,
                    "ObjectTest", TEST_TYPE.FAILING,
                    "OrderedCollectionInspectorTest", TEST_TYPE.FAILING,
                    "OrderedCollectionTest", TEST_TYPE.PASSING,
                    "OrderedDictionaryTest", TEST_TYPE.PASSING,
                    "PackageDependencyTest", TEST_TYPE.NOT_TERMINATING,
                    "PackagePaneBrowserTest", TEST_TYPE.PASSING,
                    "ParserEditingTest", TEST_TYPE.PASSING,
                    "PasteUpMorphTest", TEST_TYPE.FAILING,
                    "PCCByCompilationTest", TEST_TYPE.IGNORE,
                    "PCCByLiteralsTest", TEST_TYPE.IGNORE,
                    "PluggableMenuItemSpecTests", TEST_TYPE.PASSING,
                    "PluggableTextMorphTest", TEST_TYPE.INCONSISTENT,
                    "PNGReadWriterTest", TEST_TYPE.FAILING,
                    "PointTest", TEST_TYPE.FAILING,
                    "PolygonMorphTest", TEST_TYPE.PASSING,
                    "PreferencesTest", TEST_TYPE.REQUIRES_STARTUP,
                    "PrimCallControllerAbstractTest", TEST_TYPE.NOT_TERMINATING,
                    "ProcessSpecificTest", TEST_TYPE.NOT_TERMINATING,
                    "ProcessTerminateBug", TEST_TYPE.BROKEN,
                    "ProcessTest", TEST_TYPE.REQUIRES_STARTUP,
                    "PromiseTest", TEST_TYPE.NOT_TERMINATING,
                    "ProtoObjectTest", TEST_TYPE.PASSING,
                    "PureBehaviorTest", TEST_TYPE.NOT_TERMINATING,
                    "RandomTest", TEST_TYPE.NOT_TERMINATING,
                    "ReadStreamTest", TEST_TYPE.BROKEN,
                    "ReadWriteStreamTest", TEST_TYPE.PASSING,
                    "RecentMessagesTest", TEST_TYPE.FAILING,
                    "RectangleTest", TEST_TYPE.PASSING,
                    "ReferenceStreamTest", TEST_TYPE.FAILING,
                    "ReleaseTest", TEST_TYPE.NOT_TERMINATING,
                    "RemoteStringTest", TEST_TYPE.FAILING,
                    "RemovePrefixNamePolicyTest", TEST_TYPE.PASSING,
                    "RenderBugz", TEST_TYPE.NOT_TERMINATING,
                    "ResumableTestFailureTestCase", TEST_TYPE.PASSING,
                    "RunArrayTest", TEST_TYPE.PASSING,
                    "RWBinaryOrTextStreamTest", TEST_TYPE.FAILING,
                    "RxMatcherTest", TEST_TYPE.BROKEN,
                    "RxParserTest", TEST_TYPE.BROKEN,
                    "ScaledDecimalTest", TEST_TYPE.FAILING,
                    "ScannerTest", TEST_TYPE.PASSING,
                    "ScheduleTest", TEST_TYPE.PASSING,
                    "ScrollBarTest", TEST_TYPE.FAILING,
                    "ScrollPaneLeftBarTest", TEST_TYPE.PASSING,
                    "ScrollPaneRetractableBarsTest", TEST_TYPE.PASSING,
                    "ScrollPaneTest", TEST_TYPE.PASSING,
                    "SecureHashAlgorithmTest", TEST_TYPE.NOT_TERMINATING,
                    "SemaphoreTest", TEST_TYPE.BROKEN,
                    "SequenceableCollectionTest", TEST_TYPE.BROKEN,
                    "SetTest", TEST_TYPE.PASSING,
                    "SetWithNilTest", TEST_TYPE.FAILING,
                    "SharedQueue2Test", TEST_TYPE.NOT_TERMINATING,
                    "SHParserST80Test", TEST_TYPE.BROKEN_IN_SQUEAK,
                    "SimpleSwitchMorphTest", TEST_TYPE.PASSING,
                    "SimpleTestResourceTestCase", TEST_TYPE.PASSING,
                    "SliderTest", TEST_TYPE.FAILING,
                    "SmallIntegerTest", TEST_TYPE.PASSING,
                    "SmalltalkImageTest", TEST_TYPE.FAILING,
                    "SmartRefStreamTest", TEST_TYPE.BROKEN,
                    "SMDependencyTest", TEST_TYPE.PASSING,
                    "SMTPClientTest", TEST_TYPE.IGNORE,
                    "SocketStreamTest", TEST_TYPE.FAILING,
                    "SocketTest", TEST_TYPE.FAILING,
                    "SortedCollectionTest", TEST_TYPE.PASSING,
                    "SortFunctionTest", TEST_TYPE.PASSING,
                    "SqNumberParserTest", TEST_TYPE.FAILING,
                    "SqueakSSLTest", TEST_TYPE.FAILING,
                    "ST80MenusTest", TEST_TYPE.INCONSISTENT,
                    "ST80PackageDependencyTest", TEST_TYPE.BROKEN_IN_SQUEAK,
                    "StackTest", TEST_TYPE.PASSING,
                    "StandardSourceFileArrayTest", TEST_TYPE.PASSING,
                    "StandardSystemFontsTest", TEST_TYPE.BROKEN,
                    "StickynessBugz", TEST_TYPE.INCONSISTENT,
                    "StopwatchTest", TEST_TYPE.NOT_TERMINATING,
                    "StringSocketTestCase", TEST_TYPE.BROKEN,
                    "StringTest", TEST_TYPE.BROKEN,
                    "SumBugs", TEST_TYPE.FAILING,
                    "SUnitExtensionsTest", TEST_TYPE.NOT_TERMINATING,
                    "SUnitTest", TEST_TYPE.NOT_TERMINATING,
                    "SUnitToolBuilderTests", TEST_TYPE.BROKEN,
                    "SymbolTest", TEST_TYPE.PASSING,
                    "SystemChangeErrorHandlingTest", TEST_TYPE.PASSING,
                    "SystemChangeFileTest", TEST_TYPE.BROKEN,
                    "SystemChangeNotifierTest", TEST_TYPE.PASSING,
                    "SystemChangeTestRoot", TEST_TYPE.PASSING,
                    "SystemDictionaryTest", TEST_TYPE.PASSING,
                    "SystemNavigationTest", TEST_TYPE.FAILING,
                    "SystemOrganizerTest", TEST_TYPE.PASSING,
                    "SystemVersionTest", TEST_TYPE.PASSING,
                    "TestIndenting", TEST_TYPE.FAILING,
                    "TestNewParagraphFix", TEST_TYPE.INCONSISTENT,
                    "TestObjectsAsMethods", TEST_TYPE.BROKEN,
                    "TestParagraphFix", TEST_TYPE.PASSING,
                    "TestSpaceshipOperator", TEST_TYPE.PASSING,
                    "TestURI", TEST_TYPE.PASSING,
                    "TestValueWithinFix", TEST_TYPE.NOT_TERMINATING,
                    "TestVMStatistics", TEST_TYPE.PASSING,
                    "TextAlignmentTest", TEST_TYPE.PASSING,
                    "TextAnchorTest", TEST_TYPE.PASSING,
                    "TextAndTextStreamTest", TEST_TYPE.BROKEN,
                    "TextAttributesScanningTest", TEST_TYPE.FAILING,
                    "TextDiffBuilderTest", TEST_TYPE.PASSING,
                    "TextEditorTest", TEST_TYPE.INCONSISTENT,
                    "TextEmphasisTest", TEST_TYPE.PASSING,
                    "TextFontChangeTest", TEST_TYPE.PASSING,
                    "TextFontReferenceTest", TEST_TYPE.PASSING,
                    "TextKernTest", TEST_TYPE.PASSING,
                    "TextLineEndingsTest", TEST_TYPE.PASSING,
                    "TextLineTest", TEST_TYPE.PASSING,
                    "TextMorphTest", TEST_TYPE.PASSING,
                    "TextStyleTest", TEST_TYPE.PASSING,
                    "TextTest", TEST_TYPE.PASSING,
                    "ThirtyTwoBitRegisterTest", TEST_TYPE.PASSING,
                    "TileMorphTest", TEST_TYPE.INCONSISTENT,
                    "TimespanDoSpanAYearTest", TEST_TYPE.PASSING,
                    "TimespanDoTest", TEST_TYPE.PASSING,
                    "TimespanTest", TEST_TYPE.PASSING,
                    "TimeStampTest", TEST_TYPE.PASSING,
                    "TimeTest", TEST_TYPE.FAILING,
                    "ToolBuilderTests", TEST_TYPE.NOT_TERMINATING,
                    "TraitCompositionTest", TEST_TYPE.NOT_TERMINATING,
                    "TraitFileOutTest", TEST_TYPE.NOT_TERMINATING,
                    "TraitMethodDescriptionTest", TEST_TYPE.NOT_TERMINATING,
                    "TraitsTestCase", TEST_TYPE.PASSING,
                    "TraitSystemTest", TEST_TYPE.NOT_TERMINATING,
                    "TraitTest", TEST_TYPE.NOT_TERMINATING,
                    "TrueTest", TEST_TYPE.PASSING,
                    "UndefinedObjectTest", TEST_TYPE.PASSING,
                    "UnderscoreSelectorsTest", TEST_TYPE.PASSING,
                    "UnimplementedCallBugz", TEST_TYPE.PASSING,
                    "UnixFileDirectoryTests", TEST_TYPE.PASSING,
                    "UrlTest", TEST_TYPE.PASSING,
                    "UserInterfaceThemeTest", TEST_TYPE.NOT_TERMINATING,
                    "UTF16TextConverterTest", TEST_TYPE.BROKEN,
                    "UTF32TextConverterTest", TEST_TYPE.FAILING,
                    "UTF8TextConverterTest", TEST_TYPE.PASSING,
                    "UTFTextConverterWithByteOrderTest", TEST_TYPE.BROKEN,
                    "UUIDPrimitivesTest", TEST_TYPE.PASSING,
                    "UUIDTest", TEST_TYPE.PASSING,
                    "VersionNumberTest", TEST_TYPE.FAILING,
                    "WeakFinalizersTest", TEST_TYPE.PASSING,
                    "WeakIdentityKeyDictionaryTest", TEST_TYPE.PASSING,
                    "WeakMessageSendTest", TEST_TYPE.BROKEN,
                    "WeakRegistryTest", TEST_TYPE.NOT_TERMINATING,
                    "WeakSetInspectorTest", TEST_TYPE.PASSING,
                    "WeakSetTest", TEST_TYPE.FAILING,
                    "WebClientServerTest", TEST_TYPE.FAILING,
                    "WeekTest", TEST_TYPE.PASSING,
                    "WideCharacterSetTest", TEST_TYPE.BROKEN,
                    "WideStringTest", TEST_TYPE.FAILING,
                    "Win32VMTest", TEST_TYPE.PASSING,
                    "WordArrayTest", TEST_TYPE.FAILING,
                    "WorldStateTest", TEST_TYPE.NOT_TERMINATING,
                    "WriteStreamTest", TEST_TYPE.PASSING,
                    "XMLParserTest", TEST_TYPE.PASSING,
                    "YearMonthWeekTest", TEST_TYPE.PASSING,
                    "YearTest", TEST_TYPE.PASSING,};

    @Test
    public void test1AsSymbol() {
        assertEquals(image.asSymbol, asSymbol("asSymbol"));
    }

    @Test
    public void test2Numerical() {
        // Evaluate a few simple expressions to ensure that methodDictionaries grow correctly.
        for (long i = 0; i < 10; i++) {
            assertEquals(i + 1, evaluate(i + " + 1"));
        }
        assertEquals(4L, evaluate("-1 \\\\ 5"));
    }

    @Test
    public void test3ThisContext() {
        assertEquals(42L, evaluate("thisContext return: 42"));
    }

    @Test
    public void test4Ensure() {
        assertEquals(21L, evaluate("[21] ensure: [42]"));
        assertEquals(42L, evaluate("[21] ensure: [^42]"));
        assertEquals(21L, evaluate("[^21] ensure: [42]"));
        assertEquals(42L, evaluate("[^21] ensure: [^42]"));
    }

    @Test
    public void test5OnError() {
        Object result = evaluate("[self error: 'foobar'] on: Error do: [:err| ^ err messageText]");
        assertEquals("foobar", result.toString());
        assertEquals("foobar", evaluate("[[self error: 'foobar'] value] on: Error do: [:err| ^ err messageText]").toString());
        assertEquals(image.sqTrue, evaluate("[[self error: 'foobar'] on: ZeroDivide do: [:e|]] on: Error do: [:err| ^ true]"));
        assertEquals(image.sqTrue, evaluate("[self error: 'foobar'. false] on: Error do: [:err| ^ err return: true]"));
    }

    @Test
    public void test6Value() {
        assertEquals(42L, evaluate("[42] value"));
        assertEquals(21L, evaluate("[[21] value] value"));
    }

    @Test
    public void test7SUnitTest() {
        assertEquals(image.sqTrue, evaluate("(TestCase new should: [1/0] raise: ZeroDivide) isKindOf: TestCase"));
    }

    @Test
    public void test8TinyBenchmarks() {
        String resultString = evaluate("1 tinyBenchmarks").toString();
        assertTrue(resultString.contains("bytecodes/sec"));
        assertTrue(resultString.contains("sends/sec"));
    }

    @Ignore
    @Test
    public void testInspectSqueakTest() {
        runTestCase("ByteArrayTest");
    }

    @Ignore
    @Test
    public void testInspectSqueakTestSelector() {
        image.getOutput().println(evaluate("(WordArrayTest run: #testCannotPutNegativeValue) asString"));
    }

    @Test
    public void testVPassingSqueakTests() {
        List<String> failing = new ArrayList<>();
        String[] testClasses = getSqueakTests(TEST_TYPE.PASSING);
        printHeader(TEST_TYPE.PASSING, testClasses);
        for (int i = 0; i < testClasses.length; i++) {
            String result = runTestCase(testClasses[i]);
            if (!result.contains("passed")) {
                failing.add(result);
            }
        }
        failIfNotEmpty(failing);
    }

    @Test
    public void testWInconsistentSqueakTests() {
        String[] testClasses = getSqueakTests(TEST_TYPE.INCONSISTENT);
        printHeader(TEST_TYPE.INCONSISTENT, testClasses);
        for (int i = 0; i < testClasses.length; i++) {
            runTestCase(testClasses[i]);
        }
    }

    @Test
    public void testXFailingSqueakTests() {
        List<String> passing = new ArrayList<>();
        String[] testClasses = getSqueakTests(TEST_TYPE.FAILING);
        printHeader(TEST_TYPE.FAILING, testClasses);
        for (int i = 0; i < testClasses.length; i++) {
            String result = runTestCase(testClasses[i]);
            if (result.contains("passed")) {
                passing.add(result);
            }
        }
        failIfNotEmpty(passing);
    }

    @Ignore
    @Test
    public void testYBrokenSqueakTests() {
        List<String> passing = new ArrayList<>();
        String[] testClasses = getSqueakTests(TEST_TYPE.BROKEN);
        printHeader(TEST_TYPE.BROKEN, testClasses);
        for (int i = 0; i < testClasses.length; i++) {
            String result = runTestCase(testClasses[i]);
            if (!result.contains("failed with an error")) {
                passing.add(result);
            }
        }
        if (!passing.isEmpty()) {
            image.getError().println(String.join("\n", passing));
        }
    }

    @Ignore
    @Test
    public void testZNotTerminatingSqueakTests() {
        int timeoutSeconds = 15;
        List<String> passing = new ArrayList<>();
        String[] testClasses = getSqueakTests(TEST_TYPE.NOT_TERMINATING);
        printHeader(TEST_TYPE.NOT_TERMINATING, testClasses);
        for (int i = 0; i < testClasses.length; i++) {
            String testClass = testClasses[i];
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    passing.add(runTestCase(testClass));
                }
            });
            thread.start();
            long endTimeMillis = System.currentTimeMillis() + timeoutSeconds * 1000;
            while (thread.isAlive()) {
                if (System.currentTimeMillis() > endTimeMillis) {
                    image.getOutput().println("did not terminate in time");
                    thread.interrupt();
                    InvokeNode.callDepth = 0; // reset StackOverflow protection
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException t) {
                }
            }

        }
        failIfNotEmpty(passing);
    }

    @BeforeClass
    public static void setUpSqueakImageContext() {
        PrintWriter out = new PrintWriter(System.out, true);
        PrintWriter err = new PrintWriter(System.err, true);
        image = new SqueakImageContext(null, null, out, err);
        try {
            image.fillInFrom(new FileInputStream(IMAGE_PATH));
        } catch (IOException e) {
            e.printStackTrace();
        }
        patchImageForTesting();
    }

    private static void patchImageForTesting() {
        /*
         * Set author initials and disable timeout logic by patching TestCase>>#timeout:after: (uses
         * processes -> incompatible to running headless).
         */
        evaluate("Utilities setAuthorInitials: 'TruffleSqueak'");
        // TODO: run 'FileDirectory startUp'?
        // evaluate(String.format("FileDirectory setDefaultDirectory: '%s/images'", getImagesPathName()));
        Object patchResult = evaluate(
                        "TestCase addSelectorSilently: #timeout:after: withMethod: (TestCase compile: 'timeout: aBlock after: seconds ^ aBlock value' notifying: nil trailer: (CompiledMethodTrailer empty) ifFail: [^ nil]) method");
        assertNotEquals(image.nil, patchResult);
    }

    private static String getImagesPathName() {
        return System.getenv("TRUFFLESQUEAK_ROOT") + File.separator + "images";
    }

    private static Object getSmalltalkDictionary() {
        if (smalltalkDictionary == null) {
            smalltalkDictionary = image.specialObjectsArray.at0(SPECIAL_OBJECT_INDEX.SmalltalkDictionary);
        }
        return smalltalkDictionary;
    }

    private static Object getSmalltalkAssociation() {
        if (smalltalkAssociation == null) {
            smalltalkAssociation = new PointersObject(image, image.schedulerAssociation.getSqClass(), new Object[]{image.newSymbol("Smalltalk"), getSmalltalkDictionary()});
        }
        return smalltalkAssociation;
    }

    private static Object getEvaluateSymbol() {
        if (evaluateSymbol == null) {
            evaluateSymbol = asSymbol("evaluate:");
        }
        return evaluateSymbol;
    }

    private static Object getCompilerSymbol() {
        if (compilerSymbol == null) {
            compilerSymbol = asSymbol("Compiler");
        }
        return compilerSymbol;
    }

    private static Object asSymbol(String value) {
        String fakeMethodName = "fakeAsSymbol" + value.hashCode();
        CompiledCodeObject method = makeMethod(
                        new Object[]{4L, image.asSymbol, image.wrap(value), image.newSymbol(fakeMethodName), getSmalltalkAssociation()},
                        new int[]{0x21, 0xD0, 0x7C});
        return runMethod(method, getSmalltalkDictionary());
    }

    private static Object evaluate(String expression) {
        // ^ (Smalltalk at: #Compiler) evaluate: '{expression}'
        String fakeMethodName = "fakeEvaluate" + expression.hashCode();
        CompiledCodeObject method = makeMethod(
                        new Object[]{6L, getEvaluateSymbol(), getSmalltalkAssociation(), getCompilerSymbol(), image.wrap(expression), asSymbol(fakeMethodName), getSmalltalkAssociation()},
                        new int[]{0x41, 0x22, 0xC0, 0x23, 0xE0, 0x7C});
        return runMethod(method, getSmalltalkDictionary());
    }

    private static String[] getSqueakTests(String type) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < squeakTests.length; i += 2) {
            if (squeakTests[i + 1].equals(type)) {
                result.add((String) squeakTests[i]);
            }
        }
        return result.toArray(new String[0]);
    }

    private static String runTestCase(String testClassName) {
        image.getOutput().print(testClassName + ": ");
        image.getOutput().flush();
        String result;
        try {
            result = extractFailuresAndErrorsFromTestResult(evaluate(testClassName + " buildSuite run"));
        } catch (Exception e) {
            result = "failed with an error: " + e.toString();
        }
        image.getOutput().println(result);
        return testClassName + ": " + result;
    }

    private static String extractFailuresAndErrorsFromTestResult(Object result) {
        if (!(result instanceof BaseSqueakObject) || !result.toString().equals("a TestResult")) {
            return "did not return a TestResult, got " + result.toString();
        }
        BaseSqueakObject testResult = (BaseSqueakObject) result;
        List<String> output = new ArrayList<>();
        BaseSqueakObject failureArray = (BaseSqueakObject) ((BaseSqueakObject) testResult.at0(TEST_RESULT.FAILURES)).at0(1);
        for (int i = 0; i < failureArray.size(); i++) {
            BaseSqueakObject value = (BaseSqueakObject) failureArray.at0(i);
            if (value != image.nil) {
                output.add(value.at0(0) + " (E)");
            }
        }
        BaseSqueakObject errorArray = (BaseSqueakObject) ((BaseSqueakObject) testResult.at0(TEST_RESULT.ERRORS)).at0(0);
        for (int i = 0; i < errorArray.size(); i++) {
            BaseSqueakObject value = (BaseSqueakObject) errorArray.at0(i);
            if (value != image.nil) {
                output.add(value.at0(0) + " (F)");
            }
        }
        if (output.size() == 0) {
            return "passed";
        }
        return String.join(", ", output);
    }

    private static void failIfNotEmpty(List<String> list) {
        if (!list.isEmpty()) {
            fail(String.join("\n", list));
        }
    }

    private static void printHeader(String type, String[] testClasses) {
        image.getOutput().println();
        image.getOutput().println(String.format("== %s %s Squeak Tests ====================", testClasses.length, type));
    }
}

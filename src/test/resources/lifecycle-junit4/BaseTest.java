class BaseTest {
    WebDriver driver;

    @Before
    void setUp() {
        driver = new ChromeDriver();
    }

    @After
    void tearDown() {
        driver.quit();
    }

    @AfterClass
    void tearDownClass() {
        driver.quit();
    }
}
class BaseTest {
    WebDriver driver;

    @BeforeMethod
    void setUp() {
        driver = new ChromeDriver();
    }

    @AfterMethod
    void tearDown() {
        driver.quit();
    }

    @BeforeClass
    void setUpClass() {
        driver = new FirefoxDriver();
    }

    @BeforeTest
    void setUpTest() {
        driver = new ChromeDriver();
    }

    @AfterTest
    void tearDownTest() {
        driver.quit();
    }

    @BeforeSuite
    void setUpSuite() {
        driver = new ChromeDriver();
    }

    @AfterSuite
    void tearDownSuite() {
        driver.quit();
    }
}
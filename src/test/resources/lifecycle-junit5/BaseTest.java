class BaseTest {
    WebDriver driver;

    @BeforeAll
    static void setUpAll() {
        driver = new ChromeDriver();
    }

    @BeforeEach
    void setUp() {
        driver = new ChromeDriver();
    }

    @AfterEach
    void tearDown() {
        driver.quit();
    }

    @AfterAll
    static void tearDownAll() {
        driver.quit();
    }
}
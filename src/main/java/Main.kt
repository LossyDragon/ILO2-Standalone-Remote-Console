import com.hp.ilo2.remcons.Remcons
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.lang.Exception
import java.lang.StringBuilder
import java.net.*
import java.security.Security
import java.util.*
import javax.swing.JFrame
import javax.swing.WindowConstants
import kotlin.Throws

/*
Set-Cookie: hp-iLO-Login=; Expires=Sun, 01 Jan 1990 12:00:00 GMT
Set-Cookie: hp-iLO-Session=00000005:::LMQJVGLGKQGMIAAEGQHZJUORCOBVQOUZIEXNVTUO; Path=/; Secure

var sessionkey="LMQJVGLGKQGMIAAEGQHZJUORCOBVQOUZIEXNVTUO";
var sessionindex="00000005";
*/

fun main(args: Array<String>) {
    Main().main(args)
}

class Main {

    companion object {
        private const val COOKIE_FILE = "data.cook"
        private const val DEFAULT_CONFIG_PATH = "config.properties"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; rv:11.0) like Gecko"
        private const val USAGE_TEXT = "Usage: \n" +
            "- ILO2RemCon.jar <Hostname or IP> <Username> <Password>\n" +
            "- ILO2RemCon.jar -c <Path to config.properties>"
    }

    private var hostname = ""
    private var password = ""
    private var username = ""

    private val cookieManager = CookieManager()
    private val hmap = HashMap<String, String>()

    private var loginURL = ""
    private var sessionIndex = ""
    private var sessionKey = ""
    private var supercookie = ""

    private fun setHostname(name: String) {
        hostname = name
        loginURL = "https://$hostname/login.htm"
    }

    @Throws(Exception::class)
    private fun stage1() {
        SSLUtilities.trustAllHostnames()
        SSLUtilities.trustAllHttpsCertificates()

        System.setProperty("https.protocols", "TLSv1")
        System.setProperty("javax.net.debug", "all")
        Security.setProperty("jdk.tls.disabledAlgorithms", "")

        val obj = URL(loginURL)
        val con = obj.openConnection() as HttpURLConnection

        // optional default is GET
        // con.setRequestMethod("GET");

        // add request header
        con.setRequestProperty("User-Agent", USER_AGENT)
        con.setRequestProperty("Referer", loginURL)
        con.setRequestProperty("Host", hostname)
        con.setRequestProperty("Accept-Language", "de-DE")
        con.setRequestProperty("Cookie", "hp-iLO-Login=")

        val bufferedReader = BufferedReader(InputStreamReader(con.inputStream))
        val response = StringBuilder()
        bufferedReader.readLines().forEach {
            response.append(it)
        }
        bufferedReader.close()

        val res = response.toString()
        sessionKey = res.split("var sessionkey=\"").toTypedArray()[1].split("\";").toTypedArray()[0]
        sessionIndex = res.split("var sessionindex=\"").toTypedArray()[1].split("\";").toTypedArray()[0]

        println("Session key: $sessionKey")
        println("Session  ID: $sessionIndex")
    }

    @Throws(Exception::class)
    private fun stage2() {
        SSLUtilities.trustAllHostnames()
        SSLUtilities.trustAllHttpsCertificates()

        val obj = URL("https://$hostname/index.htm")
        val con = obj.openConnection() as HttpURLConnection
        con.setRequestProperty("User-Agent", USER_AGENT)
        con.setRequestProperty("Referer", loginURL)
        con.setRequestProperty("Host", hostname)
        con.setRequestProperty("Accept-Language", "de-DE")
        con.doOutput = true

        val enc2 = Base64.getMimeEncoder() // Authenticate
        val cookieVal = String.format(
            "hp-iLO-Login=%s:%s:%s:%s",
            sessionIndex,
            enc2.encodeToString(username.toByteArray()),
            enc2.encodeToString(password.toByteArray()),
            sessionKey
        )
        con.setRequestProperty("Cookie", cookieVal)

        val bufferedReader = BufferedReader(InputStreamReader(con.inputStream))
        bufferedReader.readLines().forEach {
            /* no-op */
            // TODO why is this here
        }
        bufferedReader.close()

        val cookies = cookieManager.cookieStore.cookies
        val writer = PrintWriter(COOKIE_FILE, "UTF-8")
        cookies.forEach { cookie ->
            System.out.format("Session cookie: %s: %s\n", cookie.domain, cookie)
            writer.println(cookie.toString().replace("\"", ""))
        }
        writer.close()
    }

    @Throws(Exception::class)
    private fun stage3() {
        SSLUtilities.trustAllHostnames()
        SSLUtilities.trustAllHttpsCertificates()

        // https://" + hostname + "/drc2fram.htm?restart=1
        val url = "https://$hostname/drc2fram.htm?restart=1"
        val obj = URL(url)
        val con = obj.openConnection() as HttpURLConnection

        // optional default is GET
        // con.setRequestMethod("GET");

        // add request header
        con.setRequestProperty("User-Agent", USER_AGENT)
        con.setRequestProperty("Referer", loginURL)
        con.setRequestProperty("Host", hostname)
        con.setRequestProperty("Accept-Language", "de-DE")
        if (supercookie != "") {
            con.setRequestProperty("Cookie", supercookie)
        }

        val bufferedReader = BufferedReader(InputStreamReader(con.inputStream))
        val response = StringBuilder()
        bufferedReader.readLines().forEach {
            response.append(it)
        }
        bufferedReader.close()

        val res = response.toString()
        hmap["INFO0"] = res.split("info0=\"").toTypedArray()[1].split("\";").toTypedArray()[0]
        hmap["INFO1"] = res.split("info1=\"").toTypedArray()[1].split("\";").toTypedArray()[0]
        hmap["INFO3"] = res.split("info3=\"").toTypedArray()[1].split("\";").toTypedArray()[0]
        hmap["INFO6"] = res.split("info6=\"").toTypedArray()[1].split("\";").toTypedArray()[0]
        hmap["INFO7"] = res.split("info7=").toTypedArray()[1].split(";").toTypedArray()[0]
        hmap["INFO8"] = res.split("info8=\"").toTypedArray()[1].split("\";").toTypedArray()[0]
        hmap["INFOA"] = res.split("infoa=\"").toTypedArray()[1].split("\";").toTypedArray()[0]
        hmap["INFOB"] = res.split("infob=\"").toTypedArray()[1].split("\";").toTypedArray()[0]
        hmap["INFOC"] = res.split("infoc=\"").toTypedArray()[1].split("\";").toTypedArray()[0]
        hmap["INFOD"] = res.split("infod=\"").toTypedArray()[1].split("\";").toTypedArray()[0]
        hmap["INFOM"] = res.split("infom=").toTypedArray()[1].split(";").toTypedArray()[0]
        hmap["INFOMM"] = res.split("infomm=").toTypedArray()[1].split(";").toTypedArray()[0]
        hmap["INFON"] = res.split("infon=").toTypedArray()[1].split(";").toTypedArray()[0]
        hmap["INFOO"] = res.split("infoo=\"").toTypedArray()[1].split("\";").toTypedArray()[0]
        hmap["CABBASE"] = res.split("<PARAM NAME=CABBASE VALUE=").toTypedArray()[1].split(">\"").toTypedArray()[0]

        println("CABBASE = " + hmap["CABBASE"])
    }

    @Throws(Exception::class)
    fun isValid(cookie: String?): Boolean {
        CookieHandler.setDefault(cookieManager)

        val url = "https://$hostname/ie_index.htm"
        val obj = URL(url)
        val con = obj.openConnection() as HttpURLConnection

        // optional default is GET
        // con.setRequestMethod("GET");

        // add request header
        con.setRequestProperty("User-Agent", USER_AGENT)
        con.setRequestProperty("Referer", loginURL)
        con.setRequestProperty("Host", hostname)
        con.setRequestProperty("Accept-Language", "de-DE")
        con.setRequestProperty("Cookie", cookie)

        val bufferedReader = BufferedReader(InputStreamReader(con.inputStream))
        var inputLine: String?
        val response = StringBuilder()
        while (bufferedReader.readLine().also { inputLine = it } != null) {
            response.append(inputLine)
        }
        bufferedReader.close()

        val res = response.toString()
        return !(res.contains("Login Delay") || res.contains("Integrated Lights-Out 2 Login"))
    }

    fun main(args: Array<String>) {
        var configPath: Optional<String> = Optional.empty()

        when (args.size) {
            0 -> {
                // <no args>
                // try the default config location
                configPath = Optional.of(DEFAULT_CONFIG_PATH)
            }
            2 -> {
                // -c <path>
                if (args[0] == "-c") {
                    configPath = Optional.of(args[1])
                } else {
                    println(USAGE_TEXT)
                    return
                }
            }
            3 -> {
                // <Hostname or IP> <Username> <Password>
                setHostname(args[0])
                username = args[1]
                password = args[2]
            }
            else -> {
                println(USAGE_TEXT)
                return
            }
        }

        SSLUtilities.trustAllHostnames()
        SSLUtilities.trustAllHttpsCertificates()
        CookieHandler.setDefault(cookieManager)

        if (configPath.isPresent) {
            try {
                FileInputStream(configPath.get()).use { fis ->
                    Properties().run {
                        load(fis)
                        setHostname(getProperty("hostname"))
                        username = getProperty("username")
                        password = getProperty("password")
                    }
                }
            } catch (e: Exception) {
                System.err.println("Error in reading/parsing config file!")
                e.printStackTrace()
                return
            }
        }

        try {
            try {
                val fileReader = FileReader("data.cook")
                BufferedReader(fileReader).use { br ->
                    println("Found datastore")

                    val lines = br.readLines()
                    val lastline = lines.last()

                    lines.forEach {
                        val cookieURI = URI("https://$hostname")
                        val cookie = HttpCookie(it.split("=").toTypedArray()[0], it.split("=").toTypedArray()[1])
                        cookieManager.cookieStore.add(cookieURI, cookie)
                    }

                    if (!isValid(lastline)) {
                        println("Datastore not valid, requesting Cookie")
                        stage1()
                        stage2()
                    } else {
                        supercookie = lastline
                    }
                }
            } catch (e: FileNotFoundException) {
                println("Couldn't find datastore, requesting Cookie")
                stage1()
                stage2()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            stage3()

            // hmap.put("IPADDR", hostname);
            // hmap.put("DEBUG", "suckAdIck");

            JFrame().apply {
                defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
                isVisible = true
                setBounds(0, 0, 1070, 880)
                title = "$hostname - iLO2"
            }.also {
                val rmc = Remcons(hmap)
                rmc.setHost(hostname)

                it.contentPane.add(rmc)
                rmc.init()
                rmc.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

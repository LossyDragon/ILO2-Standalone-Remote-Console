package com.hp.ilo2.remcons

import java.lang.StringBuilder
import java.util.*

@Suppress("unused")
internal class LocaleTranslator {

    private val aliases: Hashtable<String, String> = Hashtable()
    private val locales: Hashtable<String, Hashtable<Char?, String?>> = Hashtable()
    private val reverseAlias: Hashtable<String, String?> = Hashtable()
    private var selected: Hashtable<Char?, String?>? = null

    private var selectedName: String? = null
    var showgui = false
    var windows = true

    private val euro1 = " €\u001b[+4"
    private val euro2 = " €\u001b[+e"
    private val japanese = "\"@ &^ '& (* )( *\" +: :' =_ @[ [] \\ò ]\\ ^= _ó `{ {} ¥ô |õ }| ~+"

    private val belgian =
        "\u0001\u0011 \u0011\u0001 \u0017\u001a \u001a\u0017 !8 \"3 #\u001b[+3 $] %\" &1 '4 (5 )- *} +? ,m -= .< /> 0) 1! 2@ 3# 4$ 5% 6^ 7& 8* 9( :. ;, <ð =/ >ñ ?M @\u001b[+2 AQ M: QA WZ ZW [\u001b[+[ \\\u001b[+ð ]\u001b[+] ^[  _+ `\u001b[+\\  aq m; qa wz zw {\u001b[+9 |\u001b[+1 }\u001b[+0 ~\u001b[+/  £| §6 ¨{  °_ ²` ³~ ´\u001b[+'  µ\\ À\u001b[+\\Q Á\u001b[+'Q Â[Q Ã\u001b[+/Q Ä{Q È\u001b[+\\E É\u001b[+'E Ê[E Ë{E Ì\u001b[+\\I Í\u001b[+'I Î[I Ï{I Ñ\u001b[+/N Ò\u001b[+\\O Ó\u001b[+'O Ô[O Õ\u001b[+/O Ö{O Ù\u001b[+\\U Ú\u001b[+'U Û[U Ü{U Ý\u001b[+'Y à\u001b[+\\q á\u001b[+'q â[q ã\u001b[+/q ä{q ç9 è\u001b[+\\e é\u001b[+'e ê[e ë{e ì\u001b[+\\i í\u001b[+'i î[i ï{i ñ\u001b[+/n ò\u001b[+\\o ó\u001b[+'o ô[o õ\u001b[+/o ö{o ù\u001b[+\\u ú\u001b[+'u û[u ü{u ý\u001b[+'y ÿ{y"

    private val british =
        "\"@ #\\ @\" \\ð |ñ ~| £# ¦\u001b[+` ¬~ Á\u001b[+A á\u001b[+a É\u001b[+E é\u001b[+e Í\u001b[+I í\u001b[+i Ó\u001b[+O ó\u001b[+o Ú\u001b[+U ú\u001b[+u"

    private val danish =
        "\"@ $\u001b[+4 &^ '\\ (* )( *| +- -/ /& :> ;< <ð =) >ñ ?_ @\u001b[+2 [\u001b[+8 \\\u001b[+ð ]\u001b[+9 ^}  _? `+  {\u001b[+7 |\u001b[+= }\u001b[+0 ~\u001b[+]  £\u001b[+3 ¤$ §~ ¨]  ´=  ½` À+A Á=A Â}A Ã\u001b[+]A Ä]A Å{ Æ: È+E É=E Ê}E Ë]E Ì+I Í=I Î}I Ï]I Ñ\u001b[+]N Ò+O Ó=O Ô}O Õ\u001b[+]O Ö]O Ø\" Ù+U Ú=U Û}U Ü]U Ý=Y à+a á=a â}a ã\u001b[+]a ä]a å[ æ; è+e é=e ê}e ë]e ì+i í=i î}i ï]i ñ\u001b[+]n ò+o ó=o ô}o õ\u001b[+]o ö]o ø' ù+u ú=u û}u ü]u ý=y ÿ]y"

    private val finnish =
        "\"@ $\u001b[+4 &^ '\\ (* )( *| +- -/ /& :> ;< <ð =) >ñ ?_ @\u001b[+2 [\u001b[+8 \\\u001b[+- ]\u001b[+9 ^}  _? `+  {\u001b[+7 |\u001b[+ð }\u001b[+0 ~\u001b[+]  £\u001b[+3 ¤$ §` ¨]  ´=  ½~ À+A Á=A Â}A Ã\u001b[+]A Ä]A Å{ È+E É=E Ê}E Ë]E Ì+I Í=I Î}I Ï]I Ñ\u001b[+]N Ò+O Ó=O Ô}O Õ\u001b[+]O Ö]O Ù+U Ú=U Û}U Ü]U Ý=Y à+a á=a â}a ã\u001b[+]a ä]a å[ è+e é=e ê}e ë]e ì+i í=i î}i ï]i ñ\u001b[+]n ò+o ó=o ô}o õ\u001b[+]o ö]o ù+u ú=u û}u ü]u ý=y ÿ]y"

    private val french =
        "\u0001\u0011 \u0011\u0001 \u0017\u001a \u001a\u0017 !/ \"3 #\u001b[+3 $] %\" &1 '4 (5 )- *\\ ,m -6 .< /> 0) 1! 2@ 3# 4$ 5% 6^ 7& 8* 9( :. ;, <ð >ñ ?M @\u001b[+0 AQ M: QA WZ ZW [\u001b[+5 \\\u001b[+8 ]\u001b[+- ^\u001b[+9 _8 `\u001b[+7 aq m; qa wz zw {\u001b[+4 |\u001b[+6 }\u001b[+= ~\u001b[+2 £} ¤\u001b[+] §? ¨{  °_ ²` µ| Â[Q Ä{Q Ê[E Ë{E Î[I Ï{I Ô[O Ö{O Û[U Ü{U à0 â[q ä{q ç9 è7 é2 ê[e ë{e î[i ï{i ô[o ö{o ù' û[u ü{u ÿ{y"

    private val frenchCanadian =
        "\"@ #` '< /# <\\ >| ?^ @\u001b[+2 [\u001b[+[ \\\u001b[+` ]\u001b[+] ^[  `'  {\u001b[+' |~ }\u001b[+\\ ~\u001b[+; ¢\u001b[+4 £\u001b[+3 ¤\u001b[+5 ¦\u001b[+7 §\u001b[+o ¨}  «ð ¬\u001b[+6 ­\u001b[+. ¯\u001b[+, °\u001b[+ð ±\u001b[+1 ²\u001b[+8 ³\u001b[+9 ´\u001b[+/  µ\u001b[+m ¶\u001b[+p ¸]  »ñ ¼\u001b[+0 ½\u001b[+- ¾\u001b[+= À'A Á\u001b[+/A Â[A Ä}A Ç]C È'E É? Ê[E Ë}E Ì'I Í\u001b[+/I Î[I Ï}I Ò'O Ó\u001b[+/O Ô[O Ö}O Ù'U Ú\u001b[+/U Û[U Ü}U Ý\u001b[+/Y à'a á\u001b[+/a â[a ä}a ç]c è'e é\u001b[+/e ê[e ë}e ì'i í\u001b[+/i î[i ï}i ò'o ó\u001b[+/o ô[o ö}o ù'u ú\u001b[+/u û[u ü}u ý\u001b[+/y ÿ}y"

    private val german =
        "\u0019\u001a \u001a\u0019 \"@ #\\ &^ '| (* )( *} +] -/ /& :> ;< <ð =) >ñ ?_ @\u001b[+q YZ ZY [\u001b[+8 \\\u001b[+- ]\u001b[+9 ^`  _? `+  yz zy {\u001b[+7 |\u001b[+ð }\u001b[+0 ~\u001b[+] §# °~ ²\u001b[+2 ³\u001b[+3 ´=  µ\u001b[+m À+A Á=A Â`A Ä\" È+E É=E Ê`E Ì+I Í=I Î`I Ò+O Ó=O Ô`O Ö: Ù+U Ú=U Û`U Ü{ Ý=Z ß- à+a á=a â`a ä' è+e é=e ê`e ì+i í=i î`i ò+o ó=o ô`o ö; ù+u ú=u û`u ü[ ý=z"

    private val italian =
        "\"@ #\u001b[+' &^ '- (* )( *} +] -/ /& :> ;< <ð =) >ñ ?_ @\u001b[+; [\u001b[+[ \\` ]\u001b[+] ^+ _? |~ £# §| °\" à' ç: è[ é{ ì= ò; ù\\"

    private val latinAmerican =
        "\"@ &^ '- (* )( *} +] -/ /& :> ;< <ð =) >ñ ?_ @\u001b[+q [\" \\\u001b[+- ]| ^\u001b[+'  _? `\u001b[+\\  {' |` }\\ ~\u001b[+] ¡+ ¨{  ¬\u001b[+` °~ ´[  ¿= À\u001b[+\\A Á[A Â\u001b[+'A Ä{A È\u001b[+\\E É[E Ê\u001b[+'E Ë{E Ì\u001b[+\\I Í[I Î\u001b[+'I Ï{I Ñ: Ò\u001b[+\\O Ó[O Ô\u001b[+'O Ö{O Ù\u001b[+\\U Ú[U Û\u001b[+'U Ü{U Ý[Y à\u001b[+\\a á[a â\u001b[+'a ä{a è\u001b[+\\e é[e ê\u001b[+'e ë{e ì\u001b[+\\i í[i î\u001b[+'i ï{i ñ; ò\u001b[+\\o ó[o ô\u001b[+'o ö{o ù\u001b[+\\u ú[u û\u001b[+'u ü{u ý[y ÿ{y"

    private val norwegian =
        "\"@ $\u001b[+4 &^ '\\ (* )( *| +- -/ /& :> ;< <ð =) >ñ ?_ @\u001b[+2 [\u001b[+8 \\= ]\u001b[+9 ^}  _? `+  {\u001b[+7 |` }\u001b[+0 ~\u001b[+]  £\u001b[+3 ¤$ §~ ¨]  ´\u001b[+=  À+A Á\u001b[+=A Â}A Ã\u001b[+]A Ä]A Å{ Æ\" È+E É\u001b[+=E Ê}E Ë]E Ì+I Í\u001b[+=I Î}I Ï]I Ñ\u001b[+]N Ò+O Ó\u001b[+=O Ô}O Õ\u001b[+]O Ö]O Ø: Ù+U Ú\u001b[+=U Û}U Ü]U Ý\u001b[+=Y à+a á\u001b[+=a â}a ã\u001b[+]a ä]a å[ æ' è+e é\u001b[+=e ê}e ë]e ì+i í\u001b[+=i î}i ï]i ñ\u001b[+]n ò+o ó\u001b[+=o ô}o õ\u001b[+]o ö]o ø; ù+u ú\u001b[+=u û}u ü]u ý\u001b[+=y ÿ]y"

    private val portuguese =
        "\"@ &^ '- (* )( *{ +[ -/ /& :> ;< <ð =) >ñ ?_ @\u001b[+2 [\u001b[+8 \\` ]\u001b[+9 ^|  _? `}  {\u001b[+7 |~ }\u001b[+0 ~\\  £\u001b[+3 §\u001b[+4 ¨\u001b[+[  ª\" «= ´]  º' »+ À}A Á]A Â|A Ã\\A Ä\u001b[+[A Ç: È}E É]E Ê|E Ë\u001b[+[E Ì}I Í]I Î|I Ï\u001b[+[I Ñ\\N Ò}O Ó]O Ô|O Õ\\O Ö\u001b[+[O Ù}U Ú]U Û|U Ü\u001b[+[U Ý]Y à}a á]a â|a ã\\a ä\u001b[+[a ç; è}e é]e ê|e ë\u001b[+[e ì}i í]i î|i ï\u001b[+[i ñ\\n ò}o ó]o ô|o õ\\o ö\u001b[+[o ù}u ú]u û|u ü\u001b[+[u ý]y ÿ\u001b[+[y"

    private val spanish =
        "\"@ #\u001b[+3 &^ '- (* )( *} +] -/ /& :> ;< <ð =) >ñ ?_ @\u001b[+2 [\u001b[+[ \\\u001b[+` ]\u001b[+] ^{  _? `[  {\u001b[+' |\u001b[+1 }\u001b[+\\ ¡= ¨\"  ª~ ¬\u001b[+6 ´'  ·# º` ¿+ À[A Á'A Â{A Ä\"A Ç| È[E É'E Ê{E Ë\"E Ì[I Í'I Î{I Ï\"I Ñ: Ò[O Ó'O Ô{O Ö\"O Ù[U Ú'U Û{U Ü\"U Ý'Y à[a á'a â{a ä\"a ç\\ è[e é'e ê{e ë\"e ì[i í'i î{i ï\"i ñ; ò[o ó'o ô{o ö\"o ù[u ú'u û{u ü\"u ý'y ÿ\"y"

    private val swedish =
        "\"@ $\u001b[+4 &^ '\\ (* )( *| +- -/ /& :> ;< <ð =) >ñ ?_ @\u001b[+2 [\u001b[+8 \\\u001b[+- ]\u001b[+9 ^}  _? `+  {\u001b[+7 |\u001b[+ð }\u001b[+0 ~\u001b[+]  £\u001b[+3 ¤$ §` ¨]  ´=  ½~ À+A Á=A Â}A Ã\u001b[+]A Ä]A Å{ È+E É=E Ê}E Ë]E Ì+I Í=I Î}I Ï]I Ñ\u001b[+]N Ò+O Ó=O Ô}O Õ\u001b[+]O Ö]O Ù+U Ú=U Û}U Ü]U Ý=Y à+a á=a â}a ã\u001b[+]a ä]a å[ è+e é=e ê}e ë]e ì+i í=i î}i ï]i ñ\u001b[+]n ò+o ó=o ô}o õ\u001b[+]o ö]o ù+u ú=u û}u ü]u ý=y ÿ]y"

    private val swissFrench =
        "\u0019\u001a \u001a\u0019 !} \"@ #\u001b[+3 $\\ &^ '- (* )( *# +! -/ /& :> ;< <ð =) >ñ ?_ @\u001b[+2 YZ ZY [\u001b[+[ \\\u001b[+ð ]\u001b[+] ^=  _? `+  yz zy {\u001b[+' |\u001b[+7 }\u001b[+\\ ~\u001b[+=  ¢\u001b[+8 £| ¦\u001b[+1 §` ¨]  ¬\u001b[+6 °~ ´\u001b[+-  À+A Á\u001b[+-A Â=A Ã\u001b[+=A Ä]A È+E É\u001b[+-E Ê=E Ë]E Ì+I Í\u001b[+-I Î=I Ï]I Ñ\u001b[+=N Ò+O Ó\u001b[+-O Ô=O Õ\u001b[+=O Ö]O Ù+U Ú\u001b[+-U Û=U Ü]U Ý\u001b[+-Z à+a á\u001b[+-a â=a ã\u001b[+=a ä]a ç$ è+e é\u001b[+-e ê=e ë]e ì+i í\u001b[+-i î=i ï]i ñ\u001b[+=n ò+o ó\u001b[+-o ô=o õ\u001b[+=o ö]o ù+u ú\u001b[+-u û=u ü]u ý\u001b[+-z ÿ]z"

    private val swissGerman =
        "\u0019\u001a \u001a\u0019 !} \"@ #\u001b[+3 $\\ &^ '- (* )( *# +! -/ /& :> ;< <ð =) >ñ ?_ @\u001b[+2 YZ ZY [\u001b[+[ \\\u001b[+ð ]\u001b[+] ^=  _? `+  yz zy {\u001b[+' |\u001b[+7 }\u001b[+\\ ~\u001b[+=  ¢\u001b[+8 £| ¦\u001b[+1 §` ¨]  ¬\u001b[+6 °~ ´\u001b[+-  À+A Á\u001b[+-A Â=A Ã\u001b[+=A Ä]A È+E É\u001b[+-E Ê=E Ë]E Ì+I Í\u001b[+-I Î=I Ï]I Ñ\u001b[+=N Ò+O Ó\u001b[+-O Ô=O Õ\u001b[+=O Ö]O Ù+U Ú\u001b[+-U Û=U Ü]U Ý\u001b[+-Z à+a á\u001b[+-a â=a ã\u001b[+=a ä]a ç$ è+e é\u001b[+-e ê=e ë]e ì+i í\u001b[+-i î=i ï]i ñ\u001b[+=n ò+o ó\u001b[+-o ô=o õ\u001b[+=o ö]o ù+u ú\u001b[+-u û=u ü]u ý\u001b[+-z ÿ]z"

    fun createAccents(paramString1: String, paramString2: String?): String {
        val localStringBuilder = StringBuilder(256)

        for (element in paramString1) {
            if (element == '*') {
                localStringBuilder.append(paramString2)
            } else {
                localStringBuilder.append(element)
            }
        }

        return localStringBuilder.toString()
    }

    private fun parseLocaleStr(paramString: String, paramHashtable: Hashtable<Char?, String?>) {
        var j = 0
        var c: Char
        var localCharacter: Char? = null
        var localStringBuffer = StringBuffer(16)

        for (element in paramString) {
            c = element

            if (j == 0 && c != ' ') {
                j++
                localCharacter = c
            } else {
                if (j == 1 && c != ' ') {
                    if (c == ' ') c = ' '
                    localStringBuffer.append(c)
                }

                if (j == 1 && c == ' ') {
                    paramHashtable[localCharacter] = localStringBuffer.toString()
                    j = 0
                    localStringBuffer = StringBuffer(16)
                }
            }
        }

        paramHashtable[localCharacter] = localStringBuffer.toString()
    }

    private fun addLocale(paramString1: String, paramString2: String, paramString3: String) {
        val localHashtable = Hashtable<Char?, String?>()
        parseLocaleStr(paramString2, localHashtable)
        locales[paramString1] = localHashtable
        aliases[paramString3] = paramString1
        reverseAlias[paramString1] = paramString3
    }

    @Suppress("SameParameterValue")
    private fun addIsoAlias(paramString1: String, paramString2: String) {
        locales[paramString2] = locales[paramString1]
        reverseAlias[paramString2] = reverseAlias[paramString1]
    }

    @Suppress("SameParameterValue")
    private fun addAlias(paramString1: String, paramString2: String) {
        aliases[paramString2] = paramString1
        reverseAlias[paramString1] = paramString2
    }

    init {
        var str2: String? = null
        locales["en_US"] = Hashtable()

        addAlias("en_US", "English (United States)")

        addLocale("de_DE", german + euro2, "German")
        addLocale("en_GB", british + euro1, "English (United Kingdom)")
        addLocale("es_ES", spanish + euro2, "Spanish (Spain)")
        addLocale("es_MX", latinAmerican + euro2, "Spanish (Latin America)")
        addLocale("fr_FR", french + euro2, "French")
        addLocale("it_IT", italian + euro2, "Italian")
        addLocale("ja_JP", japanese, "Japanese")

        addIsoAlias("es_MX", "es_AR")
        addIsoAlias("es_MX", "es_BO")
        addIsoAlias("es_MX", "es_CL")
        addIsoAlias("es_MX", "es_CO")
        addIsoAlias("es_MX", "es_CR")
        addIsoAlias("es_MX", "es_DO")
        addIsoAlias("es_MX", "es_EC")
        addIsoAlias("es_MX", "es_GT")
        addIsoAlias("es_MX", "es_HN")
        addIsoAlias("es_MX", "es_NI")
        addIsoAlias("es_MX", "es_PA")
        addIsoAlias("es_MX", "es_PE")
        addIsoAlias("es_MX", "es_PR")
        addIsoAlias("es_MX", "es_PY")
        addIsoAlias("es_MX", "es_SV")
        addIsoAlias("es_MX", "es_UY")
        addIsoAlias("es_MX", "es_VE")

        addLocale("da_DK", danish + euro2, "Danish")
        addLocale("de_CH", swissGerman + euro2, "Swiss (German)")
        addLocale("fi_FI", finnish + euro2, "Finnish")
        addLocale("fr_BE", belgian + euro2, "French Belgium")
        addLocale("fr_CA", frenchCanadian + euro2, "French Canadian")
        addLocale("fr_CH", swissFrench + euro2, "Swiss (French)")
        addLocale("no_NO", norwegian + euro2, "Norwegian")
        addLocale("pt_PT", portuguese + euro2, "Portugese")
        addLocale("sv_SE", swedish + euro2, "Swedish")

        val propertyNames = Remcons.prop!!.propertyNames()
        var localString: String

        while (propertyNames.hasMoreElements()) {
            val currentPropName = propertyNames.nextElement() as String

            if (currentPropName == "locale.override") {
                str2 = Remcons.prop!!.getProperty("locale.override")
                println("Locale override: $str2")
            } else if (currentPropName.startsWith("locale.windows")) {
                windows = Remcons.prop!!.getProperty(currentPropName).toBoolean()
            } else if (currentPropName.startsWith("locale.showgui")) {
                showgui = Remcons.prop!!.getProperty(currentPropName).toBoolean()
            } else if (currentPropName.startsWith("locale.")) {
                localString = currentPropName.substring(7)
                val str3 = Remcons.prop!!.getProperty(currentPropName)
                println("Adding user defined local for $localString")
                addLocale(localString, str3, "$localString (User Defined)")
            }
        }

        if (str2 != null) {
            println("Trying to select locale: $str2")

            if (selectLocale(str2) != 0) {
                println("No keyboard definition for $str2")
            }
        } else {
            val locale = Locale.getDefault()

            println("Trying to select locale: $locale")

            if (selectLocale(locale.toString()) != 0) {
                println("No keyboard definition for '$locale'")
            }
        }
    }

    fun selectLocale(paramString: String): Int {
        var locale = paramString
        val str = aliases[locale]

        if (str != null) {
            locale = str
        }

        selected = locales[locale]
        selectedName = reverseAlias[locale]

        return if (selected != null) 0 else -1
    }

    fun translate(aChar: Char): String {
        var str: String? = null

        if (selected != null) {
            str = selected!![aChar]
        }

        return str ?: aChar.toString()
    }

    fun getLocales(): Array<String?> {
        val aliasCount: Int = aliases.size
        val aliasKeysArray = arrayOfNulls<String>(aliasCount)
        val aliasKeys = aliases.keys()
        var j = 0

        while (aliasKeys.hasMoreElements()) {
            aliasKeysArray[j++] = aliasKeys.nextElement()
        }

        j = 0

        while (j < aliasCount - 1) {
            for (k in j + 1 until aliasCount) {
                if (aliasKeysArray[k]!! < aliasKeysArray[j]!!) {
                    val tmp = aliasKeysArray[k]
                    aliasKeysArray[k] = aliasKeysArray[j]
                    aliasKeysArray[j] = tmp
                }
            }
            j++
        }

        return aliasKeysArray
    }

    fun getSelected(): String? {
        return selectedName
    }
}

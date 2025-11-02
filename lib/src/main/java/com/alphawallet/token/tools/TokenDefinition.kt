package com.alphawallet.token.tools

import com.alphawallet.token.entity.ActionModifier
import com.alphawallet.token.entity.As
import com.alphawallet.token.entity.AttestationDefinition
import com.alphawallet.token.entity.AttestationValidation
import com.alphawallet.token.entity.Attribute
import com.alphawallet.token.entity.ContractInfo
import com.alphawallet.token.entity.EthereumTransaction
import com.alphawallet.token.entity.EventDefinition
import com.alphawallet.token.entity.FunctionDefinition
import com.alphawallet.token.entity.MethodArg
import com.alphawallet.token.entity.NamedType
import com.alphawallet.token.entity.NonFungibleToken
import com.alphawallet.token.entity.ParseResult
import com.alphawallet.token.entity.TSAction
import com.alphawallet.token.entity.TSActivityView
import com.alphawallet.token.entity.TSOriginType
import com.alphawallet.token.entity.TSOrigins
import com.alphawallet.token.entity.TSSelection
import com.alphawallet.token.entity.TSTokenView
import com.alphawallet.token.entity.TSTokenViewHolder
import com.alphawallet.token.entity.TokenscriptContext
import com.alphawallet.token.entity.TokenscriptElement
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.EntityReference
import org.w3c.dom.Node
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.math.BigInteger
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Locale
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

class TokenDefinition {
    @JvmField
    val attributes: MutableMap<String, Attribute> = HashMap()
    protected var locale: Locale? = null

    @JvmField
    val contracts: MutableMap<String?, ContractInfo> = HashMap()
    val attestations: MutableMap<String?, AttestationDefinition> = HashMap()
    val tokenActions: MutableMap<String, TSAction> = HashMap()
    private val labels: MutableMap<String, String> = HashMap() // store plural etc for token name
    private val namedTypeLookup: MutableMap<String?, NamedType> = HashMap() // used to protect against name collision
    private val tokenViews = TSTokenViewHolder()
    private val selections: MutableMap<String, TSSelection> = HashMap()

    @JvmField
    val activityCards: Map<String, TSActivityView> = HashMap()
    private val viewContent: MutableMap<String, Element> = HashMap()

    @JvmField
    var nameSpace: String? = null
    var context: TokenscriptContext? = null

    @JvmField
    var holdingToken: String? = null
    private var actionCount = 0
    private var defaultOrigin: TSOrigins? = null

    /* the following are incorrect, waiting to be further improved
     with suitable XML, because none of these String typed class variables
     are going to be one-per-XML-file:

     - each contract <feature> normally should invoke new code modules
       e.g. when a new decentralised protocol is introduced, a new
       class to handle the protocol needs to be introduced, which owns
       it own way of specifying implementation, like marketQueueAPI.

     - tokenName is going to be selectable through filters -
       that is, it's allowed that token labels are different in the
       same asset class. There are use-cases for this.

     - each token definition XML file can incorporate multiple
       contracts, each with different network IDs.

     - each XML file can be signed multiple times, with multiple
       <KeyName>.
     */
    var keyName: String? = null
        protected set

    val functionData: List<FunctionDefinition>
        get() {
            val defs: MutableList<FunctionDefinition> =
                ArrayList()
            for (attr in attributes.values) {
                if (attr.function != null) {
                    defs.add(attr.function!!)
                }
            }

            return defs
        }

    val attestation: AttestationDefinition?
        get() = attestations[holdingToken]

    @Throws(SAXException::class)
    fun parseEvent(resolve: Element): EventDefinition {
        val ev = EventDefinition()

        for (i in 0..<resolve.attributes.length) {
            val thisAttr = resolve.attributes.item(i)
            val attrValue = thisAttr.nodeValue
            when (thisAttr.nodeName) {
                "contract" -> ev.contract = contracts[attrValue]
                "type" -> {
                    ev.type = namedTypeLookup[attrValue]
                    if (ev.type == null) {
                        throw SAXException("Event module not found: $attrValue")
                    }
                }

                "filter" -> ev.filter = attrValue
                "select" -> ev.select = attrValue
            }
        }

        return ev
    }

    fun parseFunction(
        resolve: Element,
        syntax: Syntax?,
    ): FunctionDefinition {
        val function = FunctionDefinition()
        val contract = resolve.getAttribute("contract")
        function.contract = contracts[contract]
        if (function.contract == null) {
            function.contract = contracts[holdingToken]
        }
        function.method = resolve.getAttribute("function")
        function.asDefin = parseAs(resolve)
        if (function.asDefin == As.Unknown) {
            function.namedTypeReturn = resolve.getAttribute("as")
        }
        addFunctionInputs(function, resolve)
        function.syntax = syntax
        return function
    }

    fun parseAs(resolve: Element): As =
        when (resolve.getAttribute("as").lowercase(Locale.getDefault())) {
            "signed" -> As.Signed
            "string", "utf8", "" -> As.UTF8
            "bytes" -> As.Bytes
            "e18" -> As.e18
            "e8" -> As.e8
            "e6" -> As.e6
            "e4" -> As.e4
            "e3" -> As.e3
            "e2" -> As.e2
            "bool" -> As.Boolean
            "mapping" -> As.Mapping
            "address" -> As.Address
            "uint" -> As.Unsigned
            else -> As.Unknown
        }

    fun getEventDefinition(activityName: String): EventDefinition? {
        if (activityCards.size > 0) {
            val v = activityCards[activityName]
            if (v != null) {
                return getActivityEvent(activityName)
            }
        }

        return null
    }

    fun getActivityEvent(activityCardName: String): EventDefinition {
        val av = activityCards[activityCardName]
        val ev = EventDefinition()
        ev.contract = contracts[holdingToken]
        if (av != null) {
            ev.filter = av.activityFilter
            ev.type = namedTypeLookup[av.eventName]
        }
        ev.activityName = activityCardName
        ev.parentAttribute = null
        ev.select = null
        return ev
    }

    fun hasEvents(): Boolean {
        for (attrName in attributes.keys) {
            val attr = attributes[attrName]
            if (attr!!.event != null && attr.event?.contract != null) {
                return true
            }
        }

        if (activityCards.size > 0) {
            return true
        }

        return false
    }

    // If there's no tokenId input in the call use tokenId 0
    fun useZeroForTokenIdAgnostic(
        attributeName: String,
        tokenId: BigInteger,
    ): BigInteger {
        val attr = attributes[attributeName]

        return if (!attr!!.usesTokenId()) {
            BigInteger.ZERO
        } else {
            tokenId
        }
    }

    val attestationIdFields: List<String>?
        get() {
            return if (attestations.size > 0) {
                attestation!!.replacementFieldIds
            } else {
                null
            }
        }

    val attestationCollectionKeys: List<String>?
        get() {
            return if (attestations.size > 0) {
                attestation!!.collectionKeys
            } else {
                null
            }
        }

    val attestationSchemaUID: String
        get() {
            return if (attestation != null) {
                attestation!!.schemaUID
            } else {
                ""
            }
        }

    val attestationCollectionPreHash: ByteArray?
        get() {
            return if (attestation != null) {
                attestation!!.collectionIdPreHash
            } else {
                null
            }
        }

    fun matchCollection(attestationCollectionId: String): Boolean =
        if (attestation != null) {
            attestation!!.matchCollection(attestationCollectionId)
        } else {
            false
        }

    fun addLocalAttr(attr: Attribute) {
        tokenViews.localAttributeTypes[attr.name] =
            attr // TODO: Refactor as it appears this doesn't respect scope
    }

    fun addGlobalStyle(element: Element) {
        tokenViews.globalStyle =
            getHTMLContent(element) // TODO: Refactor this as it appears global style is located elsewhere. This may have been deprecated
    }

    val isChanged: Boolean
        get() = (nameSpace != null && (nameSpace != UNCHANGED_SCRIPT) && (nameSpace != NO_SCRIPT))

    enum class Syntax {
        DirectoryString,
        IA5String,
        Integer,
        GeneralizedTime,
        Boolean,
        BitString,
        CountryString,
        JPEG,
        NumericString,
    }

    // for many occurance of the same tag, return the text content of the one in user's current language
    // FIXME: this function will break if there are nested <tagName> in the nameContainer
    fun getLocalisedString(
        nameContainer: Element,
        tagName: String?,
    ): String? {
        val nList = nameContainer.getElementsByTagNameNS(nameSpace, tagName)
        var name: Element
        var nonLocalised: String? = null
        for (i in 0..<nList.length) {
            name = nList.item(i) as Element
            val langAttr = getLocalisationLang(name)
            if (langAttr == locale!!.language) {
                return name.textContent.trim()
            } else if (langAttr == "en") {
                nonLocalised = name.textContent.trim()
            }
        }

        if (nonLocalised != null) {
            return nonLocalised
        } else {
            name = nList.item(0) as Element
            // TODO: catch the indice out of bound exception and throw it again suggesting dev to check schema
            return if (name != null) {
                name.textContent.trim()
            } else {
                null
            }
        }
    }

    fun getLocalisedNode(
        nameContainer: Element,
        tagName: String?,
    ): Node? {
        var nList = nameContainer.getElementsByTagNameNS(nameSpace, tagName)
        if (nList.length == 0) nList = nameContainer.getElementsByTagName(tagName)
        var name: Element
        var nonLocalised: Element? = null
        for (i in 0..<nList.length) {
            name = nList.item(i) as Element
            val langAttr = getLocalisationLang(name)
            if (langAttr == locale!!.language) {
                return name
            } else if (nonLocalised == null && (langAttr == "" || langAttr == "en")) {
                nonLocalised = name
            }
        }

        return nonLocalised
    }

    fun getLocalisedString(container: Element): String? {
        val nList = container.childNodes

        var nonLocalised: String? = null
        for (i in 0..<nList.length) {
            val n = nList.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) {
                val langAttr = getLocalisationLang(n as Element)
                if (langAttr == locale!!.language) {
                    return processText(n.getTextContent()).trim()
                } else if (nonLocalised == null && (langAttr == "" || langAttr == "en")) {
                    nonLocalised = n.getTextContent().trim()
                }
            }
        }

        return nonLocalised
    }

    private fun processText(text: String): String {
        // strip out whitespace and cr/lf
        return text.trim()
    }

    private fun hasAttribute(
        name: Element,
        typeAttr: String,
    ): Boolean {
        if (name.hasAttributes()) {
            for (i in 0..<name.attributes.length) {
                val thisAttr = name.attributes.item(i)
                if (thisAttr.textContent != null && thisAttr.textContent == typeAttr) {
                    return true
                }
            }
        }

        return false
    }

    private fun getLocalisationLang(name: Element): String {
        if (name.hasAttributes()) {
            for (i in 0..<name.attributes.length) {
                val thisAttr = name.attributes.item(i)
                if (thisAttr.localName == "lang") {
                    return thisAttr.textContent.trim()
                }
            }
        }

        return ""
    }

    // Empty definition
    constructor() {
        holdingToken = null
        nameSpace = NO_SCRIPT
    }

    constructor(xmlAsset: InputStream?, locale: Locale, result: ParseResult?) {
        this.locale = locale
        // guard input from bad programs which creates Locale not following ISO 639
        if (locale.language.length < 2 || locale.language.length > 3) {
            throw SAXException("Locale object wasn't created following ISO 639")
        }

        val dBuilder: DocumentBuilder

        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = true
            dbFactory.isExpandEntityReferences = true
            dbFactory.isCoalescing = true
            dBuilder = dbFactory.newDocumentBuilder()
            // DOMSignContext signContext = new DOMSignContext(privateKey, document.getDocumentElement());
            // XMLSignatureFactory ssig;
        } catch (e: ParserConfigurationException) {
            // TODO: if schema has problems (e.g. defined twice). Now, no schema, no exception.
            e.printStackTrace()
            return
        }
        val xml = dBuilder.parse(xmlAsset)
        xml.documentElement.normalize()
        determineNamespace(xml, result)

        val nList = xml.getElementsByTagNameNS(nameSpace, "token")
        actionCount = 0

        if (nList.length == 0 || nameSpace == null) {
            println("Legacy XML format - no longer supported")
            return
        }

        try {
            parseTags(xml)
            extractSignedInfo(xml)
            // scanAttestation(xml);
        } catch (e: IOException) {
            throw e
        } catch (e: SAXException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace() // catch other type of exception not thrown by this function.
            result?.parseMessage(ParseResult.ParseResultId.PARSE_FAILED)
        }
    }

    @Throws(Exception::class)
    private fun extractTags(token: Element) {
        // trawl through the child nodes, interpret each in turn
        var node = token.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                when (element.localName) {
                    "origins" -> {
                        val origin = parseOrigins(element)
                        if (origin!!.isType(TSOriginType.Contract) || origin.isType(TSOriginType.Attestation)) {
                            holdingToken =
                                origin.originName
                        }
                        defaultOrigin = origin
                    }

                    "contract" -> handleAddresses(element)
                    "label" -> labels.putAll(extractLabelTag(element))
                    "selection" -> {
                        val selection = parseSelection(element)
                        if (selection != null && selection.checkParse()) {
                            selections[selection.name] =
                                selection
                        }
                    }

                    "module" -> handleModule(element, null)
                    "cards" -> handleCards(element)
                    "attribute" -> {
                        val attr = Attribute(node, this)
                        attr.name?.let {
                            attributes[it] = attr
                        }
                    }

                    "attestation" -> {
                        val attestation = scanAttestation(element)
                        attestations[attestation.name] = attestation
                    }

                    "Signature" -> {}
                    else -> {}
                }
            }
            node = node.nextSibling
        }
    }

    @Throws(SAXException::class)
    private fun parseSelection(node: Element): TSSelection? {
        var name = ""
        var selection: TSSelection? = null
        for (i in 0..<node.attributes.length) {
            val thisAttr = node.attributes.item(i)
            when (thisAttr.localName) {
                "name", "id" -> name = thisAttr.nodeValue
                "filter" -> selection = TSSelection(thisAttr.nodeValue)
            }
        }

        if (selection != null) {
            selection.name = name
            var n = node.firstChild
            while (n != null) {
                if (n.nodeType == Node.ELEMENT_NODE) {
                    val element = n as Element
                    when (element.localName) {
                        "name" -> selection.names = extractLabelTag(element)
                        "denial" -> {
                            val denialNode = getLocalisedNode(element, "string")
                            selection.denialMessage = denialNode?.textContent?.trim()
                        }
                    }
                }
                n = n.nextSibling
            }
        }

        return selection
    }

    @Throws(Exception::class)
    private fun handleCards(cards: Element) {
        var node = cards.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val card = node as Element
                when (card.localName) {
                    "token", "token-card" -> {
                        val tv = TSTokenView(card, this)
                        tokenViews.views[tv.label] = tv
                    }

                    "card" -> extractCard(card)
                    "viewContent" ->
                        viewContent[card.getAttribute("name")] =
                            card
                }
            }
            node = node.nextSibling
        }
    }

    fun getViewContent(name: String): Element? = viewContent[name]

    @Throws(Exception::class)
    private fun processActivityView(card: Element): TSActivityView? {
        val ll = card.childNodes
        var activityView: TSActivityView? = null
        var useName: String? = ""

        for (j in 0..<ll.length) {
            val node = ll.item(j)
            if (node.nodeType != Node.ELEMENT_NODE) continue

            val element = node as Element
            when (node.getLocalName()) {
                "origins" -> {
                    val origins = parseOrigins(element)
                    if (origins!!.isType(TSOriginType.Event)) {
                        activityView = TSActivityView(origins)
                    }
                }

                "view", "item-view" -> {
                    if (activityView == null) {
                        activityView = TSActivityView(defaultOrigin)
                    }
                    if (useName!!.isEmpty()) {
                        useName = node.getLocalName()
                    }
                    activityView.addView(useName, TSTokenView(element, this))
                }

                "label" -> useName = getLocalisedString(element)
                else -> throw SAXException("Unknown tag <" + node.getLocalName() + "> tag in tokens")
            }
        }

        return activityView
    }

    private fun getLocalisedEntry(attrEntry: Map<String, String>): String? {
        // Picking order
        // 1. actual locale
        // 2. entry with no locale
        // 3. first non-localised locale
        var bestGuess: String? = null
        for (lang in attrEntry.keys) {
            if (lang == locale!!.language) return attrEntry[lang]
            if (lang == "" || (lang == "en")) bestGuess = attrEntry[lang]
        }

        if (bestGuess == null) {
            bestGuess =
                attrEntry.values.iterator().next() // first non-localised locale
        }

        return bestGuess
    }

    private fun determineNamespace(
        xml: Document,
        result: ParseResult?,
    ) {
        nameSpace = ATTESTATION

        val check = xml.childNodes
        for (i in 0..<check.length) {
            val n = check.item(i)
            if (!n.hasAttributes()) continue
            // check attributes∏
            for (j in 0..<n.attributes.length) {
                try {
                    val thisAttr = n.attributes.item(j)
                    if (thisAttr.nodeValue.contains(TOKENSCRIPT_BASE_URL)) {
                        nameSpace = thisAttr.nodeValue
                        nameSpace?.let {
                            // TODO
                            val dateIndex = it.indexOf(TOKENSCRIPT_BASE_URL) + TOKENSCRIPT_BASE_URL.length
                            val lastSeparator = it.lastIndexOf("/")
                            if ((lastSeparator - dateIndex) == 7) {
                                val format: DateFormat = SimpleDateFormat("yyyy/MM", Locale.ENGLISH)
                                val thisDate = format.parse(it.substring(dateIndex, lastSeparator))
                                val schemaDate = format.parse(TOKENSCRIPT_CURRENT_SCHEMA)

                                if (thisDate == schemaDate) {
                                    // all good
                                    result?.parseMessage(ParseResult.ParseResultId.OK)
                                } else if (thisDate.before(schemaDate)) {
                                    // still acceptable
                                    result?.parseMessage(ParseResult.ParseResultId.XML_OUT_OF_DATE)
                                } else {
                                    // cannot parse future schema
                                    result?.parseMessage(ParseResult.ParseResultId.PARSER_OUT_OF_DATE)
                                    nameSpace = null
                                }
                            } else {
                                result?.parseMessage(ParseResult.ParseResultId.PARSE_FAILED)
                                nameSpace = null
                            }
                        }

                        return
                    }
                } catch (e: Exception) {
                    result?.parseMessage(ParseResult.ParseResultId.PARSE_FAILED)
                    nameSpace = null
                    e.printStackTrace()
                }
            }
        }
    }

    val isSchemaLessThanMinimum: Boolean
        get() {
            if (nameSpace == null) {
                return true
            }

            val dateIndex =
                nameSpace!!.indexOf(TOKENSCRIPT_BASE_URL) + TOKENSCRIPT_BASE_URL.length
            val lastSeparator = nameSpace!!.lastIndexOf("/")
            if ((lastSeparator - dateIndex) == 7) {
                try {
                    val format: DateFormat =
                        SimpleDateFormat("yyyy/MM", Locale.ENGLISH)
                    val thisDate =
                        format.parse(nameSpace!!.substring(dateIndex, lastSeparator))
                    val schemaDate =
                        format.parse(TOKENSCRIPT_MINIMUM_SCHEMA)

                    return thisDate.before(schemaDate)
                } catch (e: Exception) {
                    return true
                }
            }

            return true
        }

    @Throws(Exception::class)
    private fun extractCard(card: Element) {
        val action: TSAction
        var activity: TSActivityView
        val type = card.getAttribute("type")
        when (type) {
            "token" -> {
                val tv = TSTokenView(card, this)
                tokenViews.views[tv.label] = tv
            }

            "action", "activity" -> {
                action = handleAction(card)
                action.name?.let { name ->
                    tokenActions[name] = action
                }
                setModifier(action, card)
            }

            "onboarding" -> {}
            else -> throw SAXException("Unexpected card type found: $type")
        }
    }

    @Throws(Exception::class)
    private fun setModifier(
        action: TSAction,
        card: Element,
    ) {
        val modifier = card.getAttribute("modifier")
        println("YOLESS MOD: $modifier")
        when (modifier.lowercase(Locale.getDefault())) {
            "attestation" -> action.modifier = ActionModifier.ATTESTATION
            "none", "" -> action.modifier = ActionModifier.NONE
            else -> throw SAXException("Unexpected modifier found: $modifier")
        }

        val type = card.getAttribute("type")
        println("YOLESS Type: $type")
        when (type) {
            "activity" -> action.modifier = ActionModifier.ACTIVITY
            "action" -> action.modifier = ActionModifier.NONE
            else ->
                if (action.modifier == null) {
                    action.modifier = ActionModifier.NONE
                }
        }
    }

    @Throws(Exception::class)
    private fun handleAction(action: Element): TSAction {
        val ll = action.childNodes
        val tsAction = TSAction()
        tsAction.order = actionCount
        tsAction.exclude = action.getAttribute("exclude")
        actionCount++
        for (j in 0..<ll.length) {
            val node = ll.item(j)
            if (node.nodeType != Node.ELEMENT_NODE) continue

            if (node.prefix != null && node.prefix.equals("ds", ignoreCase = true)) continue

            val element = node as Element
            when (node.getLocalName()) {
                "label" -> tsAction.name = getLocalisedString(element)
                "attribute" -> {
                    val attr = Attribute(element, this)
                    if (tsAction.attributes == null) {
                        tsAction.attributes = HashMap()
                    }
                    attr.name?.let { name ->
                        tsAction.attributes!![name] = attr
                    }
                }

                "transaction" -> handleTransaction(tsAction, element)
                "exclude" -> tsAction.exclude = element.getAttribute("selection")
                "selection" -> throw SAXException("<ts:selection> tag must be in main scope (eg same as <ts:origins>)")
                "view" -> tsAction.view = TSTokenView(element, this)
                "style" -> tsAction.style = getHTMLContent(element)
                "input" -> {
                    handleInput(element)
                    holdingToken = contracts.keys.iterator().next() // first key value
                }

                "output" -> {}
                "script" -> // misplaced script tag
                    throw SAXException("Misplaced <script> tag in Action '" + tsAction.name + "'")

                else -> throw SAXException("Unknown tag <" + node.getLocalName() + "> tag in Action '" + tsAction.name + "'")
            }
        }

        return tsAction
    }

    private fun getFirstChildElement(e: Element): Element? {
        var n = e.firstChild
        while (n != null) {
            if (n.nodeType == Node.ELEMENT_NODE) return n as Element
            n = n.nextSibling
        }

        return null
    }

    @Throws(Exception::class)
    private fun handleInput(element: Element) {
        var n = element.firstChild
        while (n != null) {
            if (n.nodeType != Node.ELEMENT_NODE) {
                n = n.nextSibling
                continue
            }
            val tokenType = n as Element
            val label = tokenType.getAttribute("label")
            when (tokenType.localName) {
                "token" -> {
                    val tokenSpec = getFirstChildElement(tokenType)
                    if (tokenSpec != null) {
                        when (tokenSpec.localName) {
                            "ethereum" -> {
                                val chainIdStr = tokenSpec.getAttribute("network")
                                val chainId = chainIdStr.toLong()
                                val ci = ContractInfo(tokenSpec.localName)
                                ci.addresses[chainId] =
                                    ArrayList(
                                        Arrays.asList(
                                            ci.contractInterface,
                                        ),
                                    )
                                contracts[label] = ci
                            }

                            "contract" -> handleAddresses(getFirstChildElement(element)!!)
                            else -> {}
                        }
                    }
                }

                else -> {}
            }
            n = n.getNextSibling()
        }
    }

    private fun handleTransaction(
        tsAction: TSAction,
        element: Element,
    ) {
        val tx = getFirstChildElement(element)
        when (tx!!.localName) {
            "transaction" ->
                if (tx.prefix == "ethereum") {
                    tsAction.function = parseFunction(tx, Syntax.IA5String)
                    tsAction.function?.asDefin = parseAs(tx)
                }

            else -> {}
        }
    }

    @Throws(SAXException::class)
    private fun processAttrs(n: Node) {
        val attr = Attribute(n as Element, this)
        if (attr.bitmask != null || attr.function != null) {
            attr.name?.let {
                attributes[it] = attr
            }
        }
    }

    private fun extractSignedInfo(xml: Document) {
        var nList =
            xml.getElementsByTagName("http://www.w3.org/2000/09/xmldsig#")
        nList = xml.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "KeyName")
        if (nList.length > 0) {
            this.keyName = nList.item(0).textContent.trim()
        }
        return // even if the document is signed, often it doesn't have KeyName
    }

    @Throws(SAXException::class)
    private fun scanAttestation(attestationNode: Node): AttestationDefinition {
        val element = attestationNode as Element
        val name = element.getAttribute("name")
        val attn = AttestationDefinition(name)

        var n = attestationNode.getFirstChild()
        while (n != null) {
            if (n.nodeType != Node.ELEMENT_NODE) {
                n = n.nextSibling
                continue
            }
            val attnElement = n as Element

            when (attnElement.localName) {
                "meta" -> // read elements of the metadata
                    attn.addMetaData(attnElement)

                "display" -> handleAttestationDisplay(attnElement)
                "eas" -> {
                    val info = attn.addAttributes(attnElement)
                    if (info != null) {
                        contracts[attn.name] = info
                    }
                }

                "key" -> attn.handleKey(attnElement)
                "collectionFields" -> attn.handleCollectionFields(attnElement)
                "idFields" -> attn.handleReplacementField(attnElement)
                "struct", "ProofOfKnowledge" -> {}
                "origins" -> {
                    // attn.origin = parseOrigins(attnElement);
                    // advance to function
                    val functionElement = getFirstChildElement(attnElement)
                    attn.function = parseFunction(functionElement!!, Syntax.IA5String)
                    attn.function.asDefin = parseAs(functionElement)
                }
            }
            n = n.getNextSibling()
        }

        return attn
    }

    private fun handleAttestationDisplay(attnElement: Element) {
    }

    private fun parseAttestationStructMembers(attnStruct: Node): List<AttnElement> {
        // get struct list
        val attnList: MutableList<AttnElement> = ArrayList()

        var n = attnStruct.firstChild
        while (n != null) {
            if (n.nodeType != Node.ELEMENT_NODE) {
                n = n.nextSibling
                continue
            }
            val e = n as Element
            val attnE = parseAttestationStruct(e)
            attnList.add(attnE)
            n = n.getNextSibling()
        }

        return attnList
    }

    private fun getElementName(attribute: Node): String {
        var name = ""
        if (attribute.hasAttributes()) {
            for (i in 0..<attribute.attributes.length) {
                val node = attribute.attributes.item(i)
                when (node.localName) {
                    "name" -> name = node.textContent.trim()
                    else -> {}
                }
            }
        }

        return name
    }

    fun getValidation(values: List<Type<*>>): AttestationValidation? {
        // legacy attestations should only have one type
        var attn: AttestationDefinition? = null
        if (attestations.size > 0) {
            attn = attestations.values.toTypedArray()[0]
        }

        if (attn == null || !namedTypeLookup.containsKey(attn.function.namedTypeReturn)) {
            return null
        }

        // get namedType for return
        val nType = namedTypeLookup[attn.function.namedTypeReturn]
        val builder = AttestationValidation.Builder()

        // find issuerkey
        val issuerKey = contracts["_IssuerKey"]
        builder.issuerKey(issuerKey?.firstAddress)

        var index = 0

        for (element in nType!!.sequence) {
            // handle magic values plus generic
            when (element.name) {
                "_issuerValid" -> builder.issuerValid(values[index++].value as Boolean)
                "_issuerAddress" -> builder.issuerAddress(values[index++].value as String)
                "_subjectAddress" -> builder.subjectAddress(values[index++].value as String)
                "_attestationId" -> builder.attestationId(values[index++].value as BigInteger)
                "isValid" -> builder.isValid((values[index++].value as Boolean))
                else -> builder.additional(element.name, values[index++].value as org.web3j.abi.datatypes.Type<*>)
            }
        }

        return builder.build()
    }

    val attestationReturnTypes: List<TypeReference<*>>
        get() {
            val returnTypes: MutableList<TypeReference<*>> =
                ArrayList()
            var attn: AttestationDefinition? = null
            if (attestations.size > 0) {
                attn = attestations.values.toTypedArray()[0]
            }

            if (attn == null || !namedTypeLookup.containsKey(attn.function.namedTypeReturn)) {
                return returnTypes
            }

            // get namedType for return
            val nType =
                namedTypeLookup[attn.function.namedTypeReturn]

            // add output params
            for (element in nType!!.sequence) {
                when (element.type) {
                    "uint", "uint256" ->
                        returnTypes.add(
                            object :
                                TypeReference<Uint256?>() {},
                        )

                    "bytes32" ->
                        returnTypes.add(
                            object :
                                TypeReference<Bytes32?>() {},
                        )

                    "bytes" ->
                        returnTypes.add(
                            object :
                                TypeReference<DynamicBytes?>() {},
                        )

                    "string" ->
                        returnTypes.add(
                            object :
                                TypeReference<Utf8String?>() {},
                        )

                    "address" ->
                        returnTypes.add(
                            object :
                                TypeReference<Address?>() {},
                        )

                    "bool" ->
                        returnTypes.add(
                            object :
                                TypeReference<Bool?>() {},
                        )

                    else -> {}
                }
            }

            return returnTypes
        }

    private fun parseAttestationStruct(attnElement: Node): AttnElement {
        val thisElement = AttnElement()
        thisElement.name = getElementName(attnElement)
        val type = processTypeName(attnElement.localName)
        when (type) {
            "struct" -> {
                thisElement.type = AttnStructType.STRUCT
                thisElement.members = parseAttestationStructMembers(attnElement)
            }

            "UTF8-String" -> thisElement.type = AttnStructType.UTF8_STRING
            "ASN1-Integer" -> thisElement.type = AttnStructType.ASN1_INTEGER
            "Octet-String" -> thisElement.type = AttnStructType.OCTET_STRING
            "signature" -> thisElement.type = AttnStructType.SIGNATURE
            "DER-Enum" -> thisElement.type = AttnStructType.DER_ENUM
            "SubjectPublicKeyInfo" -> thisElement.type = AttnStructType.SUBJECT_PUBLIC_KEY
            "ProofOfKnowledge" -> {
                thisElement.type = AttnStructType.PROOF_OF_KNOWLEDGE
                thisElement.members = parseAttestationStructMembers(attnElement)
            }

            "timestamp" -> thisElement.type = AttnStructType.TIMESTAMP
            "bytes" -> thisElement.type = AttnStructType.BYTES
            "uint" -> thisElement.type = AttnStructType.UINT
            "string" -> thisElement.type = AttnStructType.STRING
            "address" -> thisElement.type = AttnStructType.ADDRESS
            "bool" -> thisElement.type = AttnStructType.BOOL
            else -> {}
        }

        return thisElement
    }

    private fun processTypeName(tag: String): String =
        if (tag.startsWith("uint") || tag.startsWith("int")) {
            "uint"
        } else if (tag.startsWith("bytes")) {
            "bytes"
        } else {
            tag
        }

    private enum class AttnStructType {
        STRUCT,
        SUBJECT_PUBLIC_KEY,
        PROOF_OF_KNOWLEDGE,
        SIGNATURE,
        UTF8_STRING,
        ASN1_INTEGER,
        OCTET_STRING,
        DER_ENUM,
        DER_OBJECT,
        TIMESTAMP,

        // Ethereum return types
        ADDRESS,
        BYTES,
        STRING,
        UINT,
        BOOL,
    }

    private class AttnElement {
        var name: String? = null
        var data: String? = null
        var type: AttnStructType? = null
        var members: List<AttnElement>? = null
    }

    val tokenNameList: String
        get() {
            val sb = StringBuilder()
            var first = true
            for (labelKey in labels.keys) {
                if (!first) sb.append(",")
                sb.append(labelKey).append(",").append(labels[labelKey])
                first = false
            }

            return sb.toString()
        }

    fun getTokenName(count: Int): String? {
        var value: String? = null
        when (count) {
            1 ->
                value =
                    if (labels.containsKey("one")) {
                        labels["one"]
                    } else {
                        labels[""]
                    }

            2 -> {
                value = labels["two"]
                if (value != null) {
                    // drop through to 'other' if null.

                    value = labels["other"]
                }
            }

            else -> value = labels["other"]
        }

        if (value == null && labels.values.size > 0) {
            value = labels.values.iterator().next()
        }

        return value
    }

    fun getMappingMembersByKey(key: String): Map<BigInteger, String>? {
        if (attributes.containsKey(key)) {
            val attr = attributes[key]
            return attr!!.members
        }
        return null
    }

    fun getConvertedMappingMembersByKey(key: String): Map<BigInteger, String>? {
        if (attributes.containsKey(key)) {
            val convertedMembers: MutableMap<BigInteger, String> = HashMap()
            val attr = attributes[key]
            if (attr != null) {
                val members: MutableMap<BigInteger, String>? = attr.members
                members?.let {
                    for (actualValue in members.keys) {
                        members[actualValue]?.let {
                            convertedMembers[actualValue.shiftLeft(attr.bitshift).and(attr.bitmask)] = it
                        }
                    }
                }
            }

            return convertedMembers
        }
        return null
    }

    @Throws(Exception::class)
    private fun parseTags(xml: Document) {
        var n = xml.firstChild
        while (n != null) {
            if (n.nodeType != Node.ELEMENT_NODE) {
                n = n.nextSibling
                continue
            }
            when (n.localName) {
                "card" -> {
                    val action = handleAction(n as Element)
                    action.name?.let { name ->
                        tokenActions[name] = action
                    }
                }

                else -> extractTags(n as Element)
            }
            n = n.getNextSibling()
        }
    }

    private fun extractLabelTag(labelTag: Element): Map<String, String> {
        val localNames: MutableMap<String, String> = HashMap()
        // deal with plurals
        var nameNode = getLocalisedNode(labelTag, "plurals")
        if (nameNode != null) {
            for (i in 0..<nameNode.childNodes.length) {
                val node = nameNode.childNodes.item(i)
                handleNameNode(localNames, node)
            }
        } else // no plural
            {
                nameNode = getLocalisedNode(labelTag, "string")
                handleNameNode(localNames, nameNode)
            }

        return localNames
    }

    private fun handleNameNode(
        localNames: MutableMap<String, String>,
        node: Node?,
    ) {
        if (node != null && node.nodeType == Node.ELEMENT_NODE && node.localName == "string") {
            val element = node as Element
            val quantity = element.getAttribute("quantity")
            val name = element.textContent.trim()
            if (quantity != null && name != null) {
                localNames[quantity] = name
            }
        }
    }

    @Throws(SAXException::class)
    private fun parseOrigins(origins: Element): TSOrigins? {
        var tsOrigins: TSOrigins? = null
        var n = origins.firstChild
        while (n != null) {
            if (n.nodeType != Node.ELEMENT_NODE) {
                n = n.nextSibling
                continue
            }

            val element = n as Element

            when (element.localName) {
                "ethereum" -> {
                    val contract = element.getAttribute("contract")
                    tsOrigins =
                        TSOrigins
                            .Builder(TSOriginType.Contract)
                            .name(contract)
                            .build()
                }

                "event" -> {
                    val ev = parseEvent(element)
                    ev.contract = contracts[holdingToken]
                    tsOrigins =
                        TSOrigins
                            .Builder(TSOriginType.Event)
                            .name(ev.type?.name)
                            .event(ev)
                            .build()
                }

                "attestation" -> {
                    val attestationName = element.getAttribute("name")
                    tsOrigins =
                        TSOrigins
                            .Builder(TSOriginType.Attestation)
                            .name(attestationName)
                            .build()
                }

                else -> throw SAXException("Unknown Origin Type: '" + element.localName + "'")
            }
            n = n.getNextSibling()
        }

        return tsOrigins
    }

    @Throws(Exception::class)
    private fun handleAddresses(contract: Element) {
        val info = ContractInfo(contract.getAttribute("interface"))
        val name = contract.getAttribute("name")
        contracts[name] = info

        var n = contract.firstChild
        while (n != null) {
            if (n.nodeType == Node.ELEMENT_NODE) {
                val element = n as Element
                when (element.localName) {
                    "address" -> handleAddress(element, info)
                    "module" -> handleModule(element, null)
                }
            }
            n = n.nextSibling
        }
    }

    @Throws(SAXException::class)
    private fun handleModule(
        module: Node,
        namedType: String?,
    ) {
        var namedType = namedType
        var n = module.firstChild
        while (n != null) {
            if (n.nodeType == Node.ELEMENT_NODE) {
                val element = n as Element
                when (n.getNodeName()) {
                    "namedType" -> {
                        namedType = element.getAttribute("name")
                        if (namedType.length == 0) {
                            throw SAXException("namedType must have name attribute.")
                        } else if (namedTypeLookup.containsKey(namedType)) {
                            throw SAXException("Duplicate Module label: $namedType")
                        }
                        handleModule(element, namedType)
                    }

                    "type" -> {
                        if (namedType == null) throw SAXException("type sequence must have name attribute.")
                        handleModule(element, namedType)
                    }

                    "sequence" -> {
                        if (namedType == null) {
                            var contractAddress: String? = ""
                            if (contracts.size > 0) {
                                contractAddress = contracts.keys.iterator().next()
                            }
                            throw SAXException("[$contractAddress] Sequence must be enclosed within <namedType name=... />")
                        }
                        val eventDataType = handleElementSequence(element, namedType)
                        namedTypeLookup[namedType] = eventDataType
                        namedType = null
                    }

                    else -> {}
                }
            }
            n = n.nextSibling
        }
    }

    @Throws(SAXException::class)
    private fun handleElementSequence(
        c: Node,
        moduleName: String,
    ): NamedType {
        val module = NamedType(moduleName)
        var n = c.firstChild
        while (n != null) {
            if (n.nodeType == Node.ELEMENT_NODE) {
                val element = n as Element
                module.addSequenceElement(element, moduleName)
            }
            n = n.nextSibling
        }

        return module
    }

    private fun handleAddress(
        addressElement: Element,
        info: ContractInfo,
    ) {
        val networkStr = addressElement.getAttribute("network")
        var network: Long = 0L
        network = networkStr.toLong()
        val address = addressElement.textContent.lowercase(Locale.getDefault()).trim()
        var addresses: MutableList<String>? = info.addresses[network] as MutableList<String>?
        if (addresses == null) {
            addresses = ArrayList()
            info.addresses[network] = addresses
        }

        if (!addresses.contains(address)) {
            addresses.add(address)
        }
    }

    private fun getHTMLContent(content: Node): String {
        val sb = StringBuilder()

        for (i in 0..<content.childNodes.length) {
            val child = content.childNodes.item(i)
            when (child.nodeType) {
                Node.ELEMENT_NODE -> {
                    if (child.localName == "iframe") continue
                    sb.append("<")
                    sb.append(child.localName)
                    sb.append(htmlAttributes(child))
                    sb.append(">")
                    sb.append(getHTMLContent(child))
                    sb.append("</")
                    sb.append(child.localName)
                    sb.append(">")
                }

                Node.COMMENT_NODE -> {}
                Node.ENTITY_REFERENCE_NODE -> {
                    // load in external content
                    val entityRef = child.textContent.trim()
                    val ref = child as EntityReference

                    println(entityRef)
                }

                else ->
                    if (child != null && child.textContent != null) {
                        val parsed = child.textContent.replace("\u2019", "&#x2019;").trim()
                        sb.append(parsed)
                    }
            }
        }

        return sb.toString()
    }

    private fun htmlAttributes(attribute: Node): String {
        val sb = StringBuilder()
        if (attribute.hasAttributes()) {
            for (i in 0..<attribute.attributes.length) {
                val node = attribute.attributes.item(i)
                sb.append(" ")
                sb.append(node.localName)
                sb.append("=\"")
                sb.append(node.textContent.trim())
                sb.append("\"")
            }
        }

        return sb.toString()
    }

    fun parseField(
        tokenId: BigInteger,
        token: NonFungibleToken,
        functionMappings: Map<String, FunctionDefinition>?,
    ) {
        for (key in attributes.keys) {
            val attrType = attributes[key]
            var zero = BigInteger.ZERO
            try {
                if (attrType!!.function != null && functionMappings != null) {
                    // obtain this value from the token function mappings
                    val functionDef = functionMappings[attrType.function?.method]
                    var result = functionDef?.result
                    println("Result: $result")
                    if (attrType.syntax == Syntax.NumericString) {
                        if (result != null) {
                            if (result.startsWith("0x")) result = result.substring(2)
                            zero = BigInteger(result, 16)
                        }
                    }
                    token.setAttribute(
                        attrType.name,
                        NonFungibleToken.Attribute(attrType.name, attrType.label, zero, result),
                    )
                } else {
                    zero = tokenId.and(attrType.bitmask).shiftRight(attrType.bitshift)
                    token.setAttribute(
                        attrType.name,
                        NonFungibleToken.Attribute(
                            attrType.name,
                            attrType.label,
                            zero,
                            attrType.toString(zero),
                        ),
                    )
                }
            } catch (e: Exception) {
                token.setAttribute(
                    attrType!!.name,
                    NonFungibleToken.Attribute(
                        attrType.name,
                        attrType.label,
                        zero,
                        "unsupported encoding",
                    ),
                )
            }
        }
    }

    private fun addFunctionInputs(
        fDefinition: FunctionDefinition,
        eth: Element,
    ) {
        var node = eth.firstChild
        while (node != null) {
            if (node.nodeType != Node.ELEMENT_NODE) {
                node = node.nextSibling
                continue
            }
            val input = node as Element
            when (input.localName) {
                "data" -> processDataInputs(fDefinition, input)
                "to", "value" -> {
                    if (fDefinition.tx == null) fDefinition.tx = EthereumTransaction()
                    fDefinition.tx?.let {
                        it.args[input.localName] = parseTxTag(input)
                    }
                }

                else -> {}
            }
            node = node.getNextSibling()
        }
    }

    private fun parseTxTag(element: Element): TokenscriptElement {
        val tse = TokenscriptElement()
        tse.ref = element.getAttribute("ref")
        tse.value = element.textContent.trim()
        tse.localRef = element.getAttribute("local-ref")
        return tse
    }

    private fun processDataInputs(
        fd: FunctionDefinition,
        input: Element,
    ) {
        var n = input.firstChild
        while (n != null) {
            if (n.nodeType != Node.ELEMENT_NODE) {
                n = n.nextSibling
                continue
            }

            val inputElement = n as Element
            val arg = MethodArg()
            arg.parameterType = inputElement.localName
            arg.element = parseTxTag(inputElement)
            fd.parameters.add(arg)
            n = n.getNextSibling()
        }
    }

    fun parseField(
        tokenId: BigInteger,
        token: NonFungibleToken,
    ) {
        for (key in attributes.keys) {
            val attrtype = attributes[key]
            var `val` = BigInteger.ZERO
            try {
                if (attrtype!!.function != null) {
                    // obtain this from the function return, can't get it here
                    token.setAttribute(
                        attrtype.name,
                        NonFungibleToken.Attribute(
                            attrtype.name,
                            attrtype.label,
                            `val`,
                            "unsupported encoding",
                        ),
                    )
                } else {
                    `val` = tokenId.and(attrtype.bitmask).shiftRight(attrtype.bitshift)
                    token.setAttribute(
                        attrtype.name,
                        NonFungibleToken.Attribute(
                            attrtype.name,
                            attrtype.label,
                            `val`,
                            attrtype.toString(`val`),
                        ),
                    )
                }
            } catch (e: UnsupportedEncodingException) {
                token.setAttribute(
                    attrtype!!.name,
                    NonFungibleToken.Attribute(
                        attrtype.name,
                        attrtype.label,
                        `val`,
                        "unsupported encoding",
                    ),
                )
            }
        }
    }

    /**
     * Legacy interface for AppSiteController
     * Check for 'cards' attribute set
     * @param tag
     * @return
     */
    fun getCardData(tag: String): String? {
        val view = tokenViews.views["view"]

        return if (tag == "view") {
            view!!.tokenView
        } else if (tag == "style") {
            view!!.style
        } else {
            null
        }
    }

    fun hasTokenView(): Boolean = tokenViews.views.size > 0

    val views: String
        get() {
            val sb = StringBuilder()
            var first = true
            for (s in tokenViews.views.keys) {
                if (!first) sb.append(",")
                sb.append(s)
                first = false
            }

            return sb.toString()
        }

    fun getTokenView(viewTag: String?): String = tokenViews.getView(viewTag)

    fun getTSTokenView(name: String?): TSTokenView = tokenViews.getTSView(name)

    fun getTokenViewStyle(viewTag: String?): String = tokenViews.getViewStyle(viewTag)

    val tokenViewLocalAttributes: Map<String, Attribute>
        get() = tokenViews.localAttributeTypes

    fun getActions(): MutableMap<String, TSAction> = tokenActions

    fun getSelection(id: String): TSSelection? = selections[id]

    companion object {
        const val TOKENSCRIPT_MINIMUM_SCHEMA: String = "2020/06"
        const val TOKENSCRIPT_CURRENT_SCHEMA: String = "2024/01"
        const val TOKENSCRIPT_ADDRESS: String = "{TS_ADDRESS}"
        const val TOKENSCRIPT_CHAIN: String = "{TS_CHAIN}"
        const val TOKENSCRIPT_REPO_SERVER: String = "https://repo.tokenscript.org/"

        const val TOKENSCRIPT_STORE_SERVER: String =
            "https://store-backend.smartlayer.network/tokenscript/" + TOKENSCRIPT_ADDRESS + "/chain/" + TOKENSCRIPT_CHAIN + "/script-uri"

        const val TOKENSCRIPT_NAMESPACE: String =
            "http://tokenscript.org/" + TOKENSCRIPT_CURRENT_SCHEMA + "/tokenscript"

        private const val ATTESTATION = "http://attestation.id/ns/tbml"
        private const val TOKENSCRIPT_BASE_URL = "http://tokenscript.org/"

        const val TOKENSCRIPT_ERROR: String =
            "<h2 style=\"color:rgba(207, 0, 15, 1);\">TokenScript Error</h2>"
        private const val LEGACY_WARNING_TEMPLATE =
            "<html>" + TOKENSCRIPT_ERROR + "<h3>ts:\${ERR1} is deprecated.<br/>Use ts:\${ERR2}</h3>"

        const val UNCHANGED_SCRIPT: String = "<unchanged>"
        const val NO_SCRIPT: String = "<blank_script>"
    }
}

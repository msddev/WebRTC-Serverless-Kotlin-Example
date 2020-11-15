package cz.sazel.android.serverlesswebrtcandroid.jingleTurnReceiver

import android.util.Log
import com.google.gson.Gson
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader


class MyXmlParser {

    private lateinit var listener: XmlParserListener
    private val messages: MutableMap<String, String> = mutableMapOf()
    private var uuid: Long = 0
    private var jid: String = ""
    private val jistiServiceModel: MutableList<JistiServiceModel> = mutableListOf()

    fun parseXML(xmlString: String, listener: XmlParserListener) {
        this.listener = listener

        val parserFactory: XmlPullParserFactory
        try {
            parserFactory = XmlPullParserFactory.newInstance()
            parserFactory.isNamespaceAware = false
            val parser = parserFactory.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlString))
            processParsing(parser)
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun processParsing(parser: XmlPullParser) {
        messages.clear()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            var elementName: String?
            when (eventType) {
                XmlPullParser.START_DOCUMENT -> {
                    Log.d("TAG", "START_DOCUMENT")
                }
                XmlPullParser.START_TAG -> {
                    Log.d("TAG", "START_TAG")
                    elementName = parser.name

                    when (elementName) {
                        "stream:features" -> {
                            parser.nextTag()

                            when (parser.name) {
                                "mechanisms" -> {
                                    messages["xmlns"] = parser.getAttributeValue(null, "xmlns")
                                    parser.nextTag()
                                    messages["mechanism"] = parser.nextText()

                                    listener.callBack(JingleMessages.getXmlMessage(2, messages))
                                }
                                else -> {
                                    listener.callBack(JingleMessages.getXmlMessage(4))
                                }
                            }
                        }
                        "success" -> {
                            listener.callBack(JingleMessages.getXmlMessage(3))
                        }
                        "iq" -> {
                            when (parser.getAttributeValue(null, "id")) {
                                "_bind_auth_2" -> {
                                    parser.nextTag()
                                    parser.nextTag()
                                    jid = parser.nextText()

                                    listener.callBack(JingleMessages.getXmlMessage(5))
                                }
                                "_session_auth_2" -> {
                                    listener.callBack(JingleMessages.getXmlMessage(6))
                                }
                                "$uuid:sendIQ" -> {
                                    parser.nextTag()
                                    parser.nextTag()

                                    while (parser.eventType != XmlPullParser.END_TAG) {
                                        when (parser.getAttributeValue(null, "type")) {
                                            "stun" -> {
                                                jistiServiceModel.add(
                                                    JistiServiceModel(
                                                        type = parser.getAttributeValue(
                                                            null,
                                                            "type"
                                                        ),
                                                        host = parser.getAttributeValue(
                                                            null,
                                                            "host"
                                                        ),
                                                        port = parser.getAttributeValue(
                                                            null,
                                                            "port"
                                                        )
                                                    )
                                                )
                                            }
                                            "turn", "turns" -> {
                                                jistiServiceModel.add(
                                                    JistiServiceModel(
                                                        type = parser.getAttributeValue(
                                                            null,
                                                            "type"
                                                        ),
                                                        host = parser.getAttributeValue(
                                                            null,
                                                            "host"
                                                        ),
                                                        port = parser.getAttributeValue(
                                                            null,
                                                            "port"
                                                        ),
                                                        username = parser.getAttributeValue(
                                                            null,
                                                            "username"
                                                        ),
                                                        password = parser.getAttributeValue(
                                                            null,
                                                            "password"
                                                        ),
                                                        transport = parser.getAttributeValue(
                                                            null,
                                                            "transport"
                                                        ),
                                                        ttl = parser.getAttributeValue(
                                                            null,
                                                            "ttl"
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                        parser.nextTag()
                                        parser.nextTag()
                                    }

                                    Log.d("Jitsi_Server_Info", Gson().toJson(jistiServiceModel))
                                    listener.receiveTurns(jistiServiceModel)
                                }
                                "${uuid + 1}:sendIQ" -> {

                                }
                            }
                        }
                        "enabled" -> {
                            uuid = System.currentTimeMillis()
                            messages["uuid"] = uuid.toString()
                            listener.callBack(JingleMessages.getXmlMessage(7, messages))

                            messages.clear()
                            messages["uuid"] = (uuid + 1).toString()
                            messages["jid"] = jid
                            listener.callBack(JingleMessages.getXmlMessage(8, messages))
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    Log.d("TAG", "END_TAG")

                }
            }
            eventType = parser.next()
        }
    }
}


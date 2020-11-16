package cz.sazel.android.serverlesswebrtcandroid.jingleTurnReceiver

class JingleMessages {

    companion object {

        fun getXmlMessage(index: Int, params: Map<String, String>? = null): String =
            when (index) {
                1, 3 -> "<open to=\"meet.jit.si\" version=\"1.0\" xmlns=\"urn:ietf:params:xml:ns:xmpp-framing\"/>"
                2 -> "<auth mechanism=\"${params?.getValue("mechanism")}\" xmlns=\"${
                    params?.getValue(
                        "xmlns"
                    )
                }\"/>"
                4 -> "<iq id=\"_bind_auth_2\" type=\"set\" xmlns=\"jabber:client\"><bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"/></iq>"
                5 -> "<iq id=\"_session_auth_2\" type=\"set\" xmlns=\"jabber:client\"><session xmlns=\"urn:ietf:params:xml:ns:xmpp-session\"/></iq>"
                6 -> "<enable resume=\"true\" xmlns=\"urn:xmpp:sm:3\"/>"
                7 -> "<iq id=\"${params?.getValue("uuid")}:sendIQ\" to=\"meet.jit.si\" type=\"get\" xmlns=\"jabber:client\"><services xmlns=\"urn:xmpp:extdisco:1\"/></iq>"
                8 -> "<iq from=\"60793f75-9bed-46e0-8f42-9305950ae05c@meet.jit.si/2frO_KOC\" id=\"${params?.getValue("uuid")}:sendIQ\" to=\"meet.jit.si\" type=\"get\" xmlns=\"jabber:client\"><query xmlns=\"http://jabber.org/protocol/disco#info\"/></iq>"
                9 -> "<close xmlns=\"urn:ietf:params:xml:ns:xmpp-framing\"/>"
                else -> ""
            }

    }
}
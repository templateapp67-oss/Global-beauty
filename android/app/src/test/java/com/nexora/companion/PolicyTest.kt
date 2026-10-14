package com.nexora.companion

import com.nexora.companion.data.*
import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
 @Test fun approvedHttpsLinksOnly() {
  assertTrue(Links.permitted(Links.HOME))
  assertTrue(Links.permitted(Links.BUILDER))
  assertTrue(Links.services.all { Links.permitted(it.second) })
  listOf("http://nexora-beauty-network.templateapp67.chatgpt.site/", "javascript:alert(1)", "https://nexora-beauty-network.templateapp67.chatgpt.site.evil.com", "https://evil.com@nexora-beauty-network.templateapp67.chatgpt.site", "https://nexora-beauty-network.templateapp67.chatgpt.site:8443/").forEach { assertFalse(it,Links.permitted(it)) }
 }
 @Test fun uploadsRejectUnsupportedTypes() {
  assertTrue(UploadRules.allowed("image/jpeg",true))
  assertFalse(UploadRules.allowed("application/pdf",true))
  assertTrue(UploadRules.allowed("application/pdf",false))
  assertFalse(UploadRules.allowed("image/svg+xml",true))
  assertFalse(UploadRules.allowed("application/x-msdownload",false))
 }
 @Test fun optionalConsentStartsDisabled() { assertEquals(Consent(false,false,false),Consent()) }
}

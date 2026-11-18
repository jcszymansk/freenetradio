# R8 rules for the release build. Libraries with consumer rules (androidx, Media3, Room,
# coroutines) need nothing here; these cover what this code base loads by name.

# BaseDialogFragment.newInstance creates dialogs by class name through reflection.
-keep class * extends com.yuriy.openradio.shared.view.dialog.BaseDialogFragment {
    public <init>();
}

# The playlist parsers use JDOM2's SAXBuilder, which instantiates its reader and handler
# factories by name.
-keep class org.jdom2.** { *; }
# JDOM2's optional XPath (Jaxen) and StAX support is never used, and neither library is on
# the classpath (Android has no StAX).
-dontwarn org.jaxen.**
-dontwarn javax.xml.stream.**

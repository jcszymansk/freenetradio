# R8 rules for the release build. Libraries with consumer rules (androidx, Media3, Room,
# coroutines) need nothing here; these cover what this code base loads by name.

# BaseDialogFragment.newInstance creates dialogs by class name through reflection.
-keep class * extends com.yuriy.openradio.shared.view.dialog.BaseDialogFragment {
    public <init>();
}

# Stations are Java-serialized; keep what Java serialization reads by name.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
    !static !transient <fields>;
}

# The playlist parsers use JDOM2's SAXBuilder, which instantiates its reader and handler
# factories by name.
-keep class org.jdom2.** { *; }
# JDOM2's optional XPath (Jaxen) and StAX support is never used, and neither library is on
# the classpath (Android has no StAX).
-dontwarn org.jaxen.**
-dontwarn javax.xml.stream.**

package net.bytebuddy.utility.privilege;

import net.bytebuddy.build.HashCodeAndEqualsPlugin;

import java.security.PrivilegedAction;

/**
 * An action for reading a system property as a privileged action. 将系统属性作为特权操作读取的操作
 */
@HashCodeAndEqualsPlugin.Enhance
public class GetSystemPropertyAction implements PrivilegedAction<String> {

    /**
     * The property key.
     */
    private final String key;

    /**
     * Creates a new action for reading a system property.
     *
     * @param key The property key.
     */
    public GetSystemPropertyAction(String key) {
        this.key = key;
    }

    @Override
    public String run() {
        return System.getProperty(key);
    }
}

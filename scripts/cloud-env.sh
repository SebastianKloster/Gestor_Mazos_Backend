# Source this file before cloud Maven commands.
export JAVA_HOME=/workspace/toolchains/jdk-21.0.8+9
export MAVEN_USER_HOME=/workspace/.m2
# Use the platform trust store, including its egress proxy CA. Keep TLS validation.
if [[ -r /etc/ssl/certs/java/cacerts ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi

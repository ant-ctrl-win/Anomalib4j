import ai.onnxruntime.*;
import java.lang.management.*;
System.out.println("java.version=" + System.getProperty("java.version"));
System.out.println("providers=" + OrtEnvironment.getAvailableProviders());
System.out.println("inputArguments=" + ManagementFactory.getRuntimeMXBean().getInputArguments());
System.out.println("maxHeap=" + Runtime.getRuntime().maxMemory());
System.out.println("gc=" + ManagementFactory.getGarbageCollectorMXBeans().stream().map(b -> b.getName()).toList());
System.out.println("pid=" + ProcessHandle.current().pid());
Thread.sleep(3000);
/exit

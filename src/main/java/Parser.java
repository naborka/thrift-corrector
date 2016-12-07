import com.google.inject.Inject;
import com.google.inject.Injector;
import org.apache.thrift.tgrammar.ThriftIdlStandaloneSetup;
import org.apache.thrift.tgrammar.thriftIdl.*;
import org.apache.thrift.tgrammar.thriftIdl.Enum;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResourceSet;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by nabor on 02/12/2016.
 */
public class Parser {

    @Inject
    private XtextResourceSet resourceSet;

    public Parser(String[] args) {
        for (String path : args
                ) {
            parse(path);
        }
    }

    private void parse(String path) {

        Injector injector = new ThriftIdlStandaloneSetup().createInjectorAndDoEMFRegistration();
        XtextResourceSet resourceSet = injector.getInstance(XtextResourceSet.class);

        Resource resource = resourceSet.getResource(URI.createURI(path), true);

//        ((Document) resource.getContents().get(0)).getHeaders().clear();

        EList<Definition> definitions = ((Document) resource.getContents().get(0)).getDefinitions();
        Map<String, Definition> stringStucts = new HashMap<>();
        Set<String> innerTypes = new HashSet<>();

        for (Definition definition : definitions
                ) {
            if (definition instanceof Struct || definition instanceof Enum) {
                stringStucts.put(definition.getIdentifier(), definition);
            }
        }

        for (int i = 0; i < definitions.size(); i++) {
            boolean flag = false;
            if (definitions.get(i) instanceof Struct) {
                innerTypes = new HashSet<>();
                for (Field f : ((Struct) definitions.get(i)).getFields()) {
                    try {
                        if (stringStucts.keySet().contains(f.getFieldType().getIdentifier())) {
                            innerTypes.add(f.getFieldType().getIdentifier());
                        }
                        innerTypes.addAll(getInnerFields(f.getFieldType()));
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (innerTypes.size() > 0) {
                for (String stringType : innerTypes
                        ) {
                    Integer mainDefinitionId = definitions.indexOf(definitions.get(i));
                    Integer comparableDefinitionId = definitions.indexOf(stringStucts.get(stringType));
                    if (comparableDefinitionId > mainDefinitionId) {
                        definitions.move(mainDefinitionId, definitions.get(definitions.indexOf(stringStucts.get(stringType))));
                        flag = true;
                    }
                }
            }
            if (flag) {
                i = -1;
            }
        }

        Resource newResource = resourceSet.createResource(URI.createURI("gen/"+path));
        newResource.getContents().add(resource.getContents().get(0));
        try {
            newResource.save(Collections.EMPTY_MAP);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> getInnerFields(FieldType f) throws InvocationTargetException, IllegalAccessException {
        List<String> fieldList = new ArrayList<>();
        Method getFieldTypeMethod = null;
        Object container;

        if (f != null) {
            if (f.getDefType() != null) {
                if (f.getDefType().getContainer() != null) {
                    container = f.getDefType().getContainer();
                    Method[] declaredMethods = container.getClass().getDeclaredMethods();
                    List<Method> methods = Arrays.asList(declaredMethods);

                    try {
                        getFieldTypeMethod = container.getClass().getMethod("getFieldType");
                    } catch (NoSuchMethodException e) {
                        // NOPE
                    }

                    if (getFieldTypeMethod != null) {
                        FieldType invokeResult = (FieldType) getFieldTypeMethod.invoke(container);
                        fieldList.add(invokeResult.getIdentifier());
                        getInnerFields(invokeResult);
                    }

                }
            }
        }
        return fieldList;
    }
}
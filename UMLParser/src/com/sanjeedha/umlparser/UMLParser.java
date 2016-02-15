package com.sanjeedha.umlparser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.io.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.body.*;
import java.util.HashMap;

import java.nio.charset.StandardCharsets;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import net.sourceforge.plantuml.SourceStringReader;

import com.github.javaparser.ast.body.ModifierSet;
import com.github.javaparser.ast.AccessSpecifier;

import com.sun.deploy.util.StringUtils;

public class UMLParser {

    public static void main(String[] args) throws Exception {

        UMLParser parser = new UMLParser();

        if (args.length == 1) {
            String outputImage = "output.png";

            File folderName = new File(args[0]);

            if (folderName.isDirectory()) {
                parser.parse(folderName, outputImage);
            } else {
                throw new FileNotFoundException("Please enter a valid folder path");
            }
        } else if (args.length == 2) {
            File folderName = new File(args[0]);
            String outputImage = args[1];

            if (folderName.isDirectory()) {
                parser.parse(folderName, outputImage);
            } else {
                throw new FileNotFoundException("Please enter a valid folder path");
            }
        }

    }

    private void parse(File folderName, String outputImage) throws Exception {
        ArrayList<String> javaFiles = getJavaFiles(folderName);

        StringBuilder inputStringBuilder = new StringBuilder();
        for (String file : javaFiles) {
            FileInputStream fis = new FileInputStream(file);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            String line = bufferedReader.readLine();
            while(line != null){
                inputStringBuilder.append(line);inputStringBuilder.append('\n');
                line = bufferedReader.readLine();
            }
        }

        String ipStreamStr = inputStringBuilder.toString();

        ipStreamStr = ipStreamStr.replace("import", "// import");
        ipStreamStr = ipStreamStr.replace("package", "// package");

        InputStream in = new ByteArrayInputStream(ipStreamStr.getBytes(StandardCharsets.UTF_8));

        CompilationUnit cu;
        try {
            // parse the file
            cu = JavaParser.parse(in);
        } finally {
            in.close();
        }

        ArrayList classNames = getClasses(cu);
        ArrayList interfaceNames = getInterfaces(cu);
        ArrayList enumNames = getEnums(cu);

        ArrayList bigArray = new ArrayList();

        for (TypeDeclaration typeDec : cu.getTypes()) {
            List<BodyDeclaration> members = typeDec.getMembers();
            ArrayList extendsList = getExtendsListForClass(typeDec);
            ArrayList implementsList = getImplementsForClass(typeDec);

            ArrayList enumList = new ArrayList();

            if (members != null) {
                ArrayList fieldList = new ArrayList();
                ArrayList constList = new ArrayList();
                ArrayList methodList = new ArrayList();

                for (BodyDeclaration member : members) {
                    if (member instanceof FieldDeclaration) {
                        ArrayList<String> fieldInfo = getFieldInfo(member, classNames, interfaceNames);
                        fieldList.addAll(fieldInfo);

                    } else if (member instanceof ConstructorDeclaration) {
                        HashMap<String, String> constMap = getConstructorInfo(member);
                        constList.add(constMap);

                    } else if (member instanceof MethodDeclaration) {
                        HashMap<String, String> methodMap = getMethodInfo(member);
                        methodList.add(methodMap);
                    }
                }

                HashMap<String, ArrayList> test = new HashMap<String, ArrayList>();
                test.put("methods", methodList);
                test.put("constructors", constList);
                test.put("extends", extendsList);
                test.put("fields", fieldList);
                test.put("implements", implementsList);

                ArrayList clsName = new ArrayList();
                clsName.add(typeDec.getName());
                test.put("name", clsName);
                bigArray.add(test);
            } else if (typeDec instanceof EnumDeclaration) {
                HashMap<String, ArrayList> enumMap = getEnumInfo(typeDec);
                bigArray.add(enumMap);
            }
        }


        String mainGrammar = "@startuml\n";
        HashMap<String, ArrayList> depHash = new HashMap<String, ArrayList>();
        HashMap<String, ArrayList> manyRelationship = new HashMap<String, ArrayList>();
        HashMap<String, ArrayList> oneRelationship = new HashMap<String, ArrayList>();

        for (int i = 0; i < bigArray.size(); i++) {
            HashMap<String, ArrayList> test1 = (HashMap<String, ArrayList>) bigArray.get(i);
            ArrayList classNameList = test1.get("name");
            String className = (String) classNameList.get(0);

            ArrayList fieldsList = test1.get("fields");
            ArrayList methodsList = test1.get("methods");
            ArrayList constructorsList = test1.get("constructors");
            ArrayList extendsList = test1.get("extends");
            ArrayList implementsList = test1.get("implements");
            Set<String> fieldNames = new HashSet<String>();
            Set<String> methodNames = new HashSet<String>();
            Set<String> usesClass = new HashSet<String>();
            Set<String> dependencies = new HashSet<String>();
            String grammar = "";

            if (enumNames.contains(className)) {
                mainGrammar += "enum " + className + " {\n";
                ArrayList enumFieldsList = test1.get("enumFields");
                for (int a = 0; a < enumFieldsList.size(); a++) {
                    mainGrammar += enumFieldsList.get(a) + "\n";
                }
                mainGrammar += "}\n";
                continue;
            }

            for (int a = 0; a < fieldsList.size(); a++) {
                HashMap<String, String> f = (HashMap<String, String>) fieldsList.get(a);
                String fName = f.get("name");
                fieldNames.add(fName.toLowerCase());
            }

            for (int a = 0; a < methodsList.size(); a++) {
                HashMap<String, String> f = (HashMap<String, String>) methodsList.get(a);
                String mName = f.get("name");
                methodNames.add(mName.toLowerCase());
            }

            if (interfaceNames.contains(className)) {
                grammar = "interface " + className + " {\n";
            } else {
                grammar = "class " + className + " {\n";
            }
            for (int a = 0; a < constructorsList.size(); a++) {
                HashMap<String, String> c = (HashMap<String, String>) constructorsList.get(a);
                String cName = c.get("name");
                String cAccess = c.get("access");
                String[] cParameters = c.get("parameters").split(",");
                String[] cParameterTypes = c.get("parameterTypes").split(",");

                for (int b = 0; b < cParameterTypes.length; b++) {
                    if (interfaceNames.contains(cParameterTypes[b])) {
                        usesClass.add(cParameterTypes[b]);
                    }
                }

                ArrayList paramsReorder = new ArrayList();

                for (String p: cParameters) {
                    if (p.equals("")) {
                        continue;
                    }
                    String[] paraTypeName = p.split(" ");
                    String paraType = paraTypeName[0];
                    String paraName = paraTypeName[1];
                    paramsReorder.add(paraName + ":" + paraType);
                }

                String paramsReorderStr = StringUtils.join(paramsReorder, ",");

                String cSign = "+";
                if (cAccess.equals("private")) {
                    cSign = "-";
                }
                grammar += cSign + " " + cName + "(" + paramsReorderStr + ")" + "\n";
            }
            for (int a = 0; a < methodsList.size(); a++) {
                HashMap<String, String> m = (HashMap<String, String>) methodsList.get(a);
                String mName = m.get("name");
                String mType = m.get("type");
                String mAccess = m.get("access");
                String[] mParameterTypes = m.get("parameterTypes").split(",");
                String[] mParameters = m.get("parameters").split(",");
                String[] uses = m.get("uses").split(",");

                ArrayList paramsReorder = new ArrayList();

                for (String p: mParameters) {
                    if (p.equals("")) {
                        continue;
                    }
                    String[] paraTypeName = p.split(" ");
                    String paraType = paraTypeName[0];
                    String paraName = paraTypeName[1];
                    paramsReorder.add(paraName + ":" + paraType);
                    if (enumNames.contains(paraType)) {
                        mainGrammar += className + " ..> " + paraType + "\n";
                    }
                }

                String paramsReorderStr = StringUtils.join(paramsReorder, ",");

                for (int b = 0; b < mParameterTypes.length; b++) {
                    if (interfaceNames.contains(mParameterTypes[b])) {
                        usesClass.add(mParameterTypes[b]);
                    }
                }

                for (int b = 0; b < uses.length; b++) {
                    if (interfaceNames.contains(uses[b])) {
                        usesClass.add(uses[b]);
                    }
                }

                if ((mName.startsWith("get") || mName.startsWith("set")) && mAccess.equals("public") && fieldNames.contains(mName.substring(3).toLowerCase())) {
                    continue;
                }
                String mSign = "+";
                if (mAccess.equals("private")) {
//                    mSign = "-";
                    continue;
                }
                grammar += mSign + " " + mName + "(" + paramsReorderStr + "):" + mType + "\n";
            }
            for (int a = 0; a < fieldsList.size(); a++) {
                HashMap<String, String> f = (HashMap<String, String>) fieldsList.get(a);
                String fName = f.get("name");
                String fType = f.get("type");
                String fAccess = f.get("access");
                String oneOne = f.get("oneOne");
                String oneMany = f.get("oneMany");
                String fSign = "-";
                if (fAccess.equals("public")) {
                    fSign = "+";
                } else if (fAccess.equals("protected")) {
                    fSign = "~";
//                    continue;
                }
                String tempMethodGet = "get" + fName.toLowerCase();
                String tempMethodSet = "set" + fName.toLowerCase();
                if (fAccess.equals("private") && (methodNames.contains(tempMethodGet) || methodNames.contains(tempMethodSet))) {
                    fSign = "+";
                }
                if (!fType.startsWith("Collection<") && !fAccess.equals("protected")) {
                    grammar += fSign + " " + fName + ":" + fType + "\n";
                }
                if (interfaceNames.contains(fType)) {
                    usesClass.add(fType);
                }

                if (classNames.contains(oneOne) || interfaceNames.contains(oneOne)) {
                    dependencies.add(oneOne);
                    if (oneRelationship.containsKey(className)) {
                        ArrayList tmp = oneRelationship.get(className);
                        tmp.add(oneOne);
                        oneRelationship.put(className, tmp);
                    } else {
                        ArrayList tmp = new ArrayList();
                        tmp.add(oneOne);
                        oneRelationship.put(className, tmp);
                    }
                }
                if (classNames.contains(oneMany) || interfaceNames.contains(oneMany)) {
                    dependencies.add(oneMany);
                    if (manyRelationship.containsKey(className)) {
                        ArrayList tmp = manyRelationship.get(className);
                        tmp.add(oneMany);
                        manyRelationship.put(className, tmp);
                    } else {
                        ArrayList tmp = new ArrayList();
                        tmp.add(oneMany);
                        manyRelationship.put(className, tmp);
                    }
                }

            }
            grammar += "}\n";
            for (int k = 0; k < extendsList.size(); k++) {
                String e = (String) extendsList.get(k);
                grammar += e + " <|-- " + className + "\n";
            }
            for (int k = 0; k < implementsList.size(); k++) {
                String e = (String) implementsList.get(k);
                grammar += e + " <|.. " + className + "\n";
            }
            for (String s:usesClass) {
                if (interfaceNames.contains(className)) {
                    continue;
                }
                grammar += className + " ..> " + s + "\n";
            }

            mainGrammar = mainGrammar + grammar;
        }

        for (Map.Entry<String, ArrayList> entry : manyRelationship.entrySet()) {
            String key = entry.getKey();
            ArrayList<String> value = entry.getValue();

            for (String c: value) {
                if (manyRelationship.containsKey(c) && manyRelationship.get(c).contains(key)) {
                    // many - many
                    mainGrammar += key + "\"*\" -- \"*\"" + c + "\n";
                } else if (oneRelationship.containsKey(c) && oneRelationship.get(c).contains(key)) {
                    // one - many
                    ArrayList<String> tmp = oneRelationship.get(c);
                    tmp.remove(key);
                    oneRelationship.put(c, tmp);
                    mainGrammar += key + "\"1\" -- \"*\"" + c + "\n";
                } else {
                    // one - many
                    mainGrammar += key + "\"1\" -- \"*\"" + c + "\n";
                }
            }
        }

        ArrayList<String> visited = new ArrayList<String>();
        for (Map.Entry<String, ArrayList> entry : oneRelationship.entrySet()) {
            String key = entry.getKey();
            ArrayList<String> value = entry.getValue();

            for (String c: value) {
                // one - one
                if (visited.contains(key + "-" + c) || visited.contains(c + "-" + key)) {
                    continue;
                }
                mainGrammar += key + "\"1\" -- \"1\"" + c + "\n";
                visited.add(key + "-" + c);
                visited.add(c + "-" + key);
            }
        }

        mainGrammar += "@enduml";

        OutputStream png = null;
        try {
            png = new FileOutputStream(outputImage);
            SourceStringReader reader = new SourceStringReader(mainGrammar);
            String desc = reader.generateImage(png);
        }
        catch (FileNotFoundException e) {
            System.out.println("File Not Found");
        }
        catch (IOException e) {
            System.out.println("IO Exception");
        }
    }

    public static ArrayList<String> getJavaFiles(final File folder) {
        ArrayList fileList = new ArrayList();
        for (final File fileEntry : folder.listFiles()) {
            String filePath = fileEntry.getPath();
            if (filePath.toLowerCase().endsWith(".java")) {
                fileList.add(filePath);
            }
        }
        return fileList;
    }

    public static ArrayList<String> getClasses(CompilationUnit cu)
    {
        ArrayList classList = new ArrayList();
        for (TypeDeclaration typeDec : cu.getTypes()) {
            if(typeDec instanceof ClassOrInterfaceDeclaration && !((ClassOrInterfaceDeclaration) typeDec).isInterface())
            {
                classList.add(typeDec.getName());
            }
        }
        return classList;
    }

    private ArrayList getEnums(CompilationUnit cu) {
        ArrayList enumList = new ArrayList();
        for (TypeDeclaration typeDec : cu.getTypes()) {
            if(typeDec instanceof EnumDeclaration)
            {
                enumList.add(typeDec.getName());
            }
        }
        return enumList;
    }

    public static ArrayList<String> getInterfaces(CompilationUnit cu)
    {
        ArrayList interfaceList = new ArrayList();
        for (TypeDeclaration typeDec : cu.getTypes()){
            if(typeDec instanceof ClassOrInterfaceDeclaration && ((ClassOrInterfaceDeclaration) typeDec).isInterface())
            {
                interfaceList.add(typeDec.getName());
            }
        }
        return interfaceList;
    }


    public static ArrayList<String> getExtendsListForClass(TypeDeclaration typeDec)
    {
        ArrayList extendsList = new ArrayList();
        if (typeDec instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cls = (ClassOrInterfaceDeclaration) typeDec;
            List<ClassOrInterfaceType> extendList = cls.getExtends();
            for (ClassOrInterfaceType c : extendList) {
                extendsList.add(c.getName());
            }
        }
        return extendsList;
    }

    public static ArrayList<String> getImplementsForClass(TypeDeclaration typeDec)
    {
        ArrayList implementsList = new ArrayList();
        if (typeDec instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cls = (ClassOrInterfaceDeclaration) typeDec;
            List<ClassOrInterfaceType> implementList = cls.getImplements();
            for (ClassOrInterfaceType c : implementList) {
                implementsList.add(c.getName());
            }
        }
        return implementsList;
    }

    public static ArrayList<String> getFieldInfo(BodyDeclaration member, ArrayList classNames, ArrayList interfaceNames)
    {
        ArrayList fieldList = new ArrayList();
        FieldDeclaration field = (FieldDeclaration) member;
        AccessSpecifier accessSpecifier = ModifierSet.getAccessSpecifier(field.getModifiers());

        String oneOne = "";
        String oneMany = "";
        String fTypeStr = field.getType().toString();
        Type fType = field.getType();

        if (classNames.contains(fTypeStr) || interfaceNames.contains(fTypeStr)) {
            // relationship
            oneOne = fTypeStr;
        }
        else {
            if (fType instanceof ReferenceType) {
                if (((ReferenceType) fType).getType() instanceof ClassOrInterfaceType) {
                    List typeArgs = ((ClassOrInterfaceType) ((ReferenceType) fType).getType()).getTypeArgs();
                    if (typeArgs.size() > 0) {
                        String fTypeStr1 = typeArgs.get(0).toString();
                        if (classNames.contains(fTypeStr1) || interfaceNames.contains(fTypeStr1)) {
                            // 1-many relationship
                            oneMany = fTypeStr1;
                        }
                    }
                }
            }
        }


        for(VariableDeclarator v: field.getVariables()) {
            String fieldName = v.getId().getName();
            String fieldType = field.getType().toString();
            HashMap<String, String> fieldMap = new HashMap<String, String>();
            fieldMap.put("type", fieldType);
            fieldMap.put("name", fieldName);
            fieldMap.put("oneOne", oneOne);
            fieldMap.put("oneMany", oneMany);
            fieldMap.put("access", accessSpecifier.getCodeRepresenation());

            fieldList.add(fieldMap);
        }

        return fieldList;
    }


    public static HashMap<String, String> getMethodInfo(BodyDeclaration member)
    {
        HashMap<String, String> methodmap = new HashMap<String, String>();
        MethodDeclaration method = (MethodDeclaration) member;
        String methodName = method.getName();
        String methodType = method.getType().toString();
        BlockStmt body = method.getBody();

        if (body != null) {
            List<Statement> methodStatements = method.getBody().getStmts();
            ArrayList uses = new ArrayList();

            for (Statement st: methodStatements) {
                String tmpSt = st.toString();
                if (tmpSt.contains("new ")) {
                    uses.add(tmpSt.split(" ")[0]);
                }
            }

            methodmap.put("uses", StringUtils.join(uses, ","));
        } else {
            methodmap.put("uses", "");
        }

        ArrayList parameters = new ArrayList();
        ArrayList parameterTypes = new ArrayList();
        for (Parameter p : method.getParameters()) {
            parameters.add(p.toString());
            parameterTypes.add(p.getType().toString());
        }

        AccessSpecifier accessSpecifier = ModifierSet.getAccessSpecifier(method.getModifiers());

        methodmap.put("type", methodType);
        methodmap.put("name", methodName);
        methodmap.put("access", accessSpecifier.getCodeRepresenation());
        methodmap.put("parameters", StringUtils.join(parameters, ","));
        methodmap.put("parameterTypes", StringUtils.join(parameterTypes, ","));

        return methodmap;

    }

    public static HashMap<String, String> getConstructorInfo(BodyDeclaration member)
    {
        HashMap<String, String> constructorMap = new HashMap<String, String>();
        ConstructorDeclaration constructor = (ConstructorDeclaration) member;
        String constructorName = constructor.getName();

        ArrayList parameters = new ArrayList();
        ArrayList parameterTypes = new ArrayList();
        for (Parameter p : constructor.getParameters()) {
            parameters.add(p.toString());
            parameterTypes.add(p.getType().toString());
        }

        AccessSpecifier accessSpecifier = ModifierSet.getAccessSpecifier(constructor.getModifiers());

        constructorMap.put("name", constructorName);
        constructorMap.put("access", accessSpecifier.getCodeRepresenation());
        constructorMap.put("parameters", StringUtils.join(parameters, ","));
        constructorMap.put("parameterTypes", StringUtils.join(parameterTypes, ","));

        return constructorMap;

    }

    private HashMap<String,ArrayList> getEnumInfo(BodyDeclaration member) {
        HashMap<String, ArrayList> enumMap = new HashMap<String, ArrayList>();
        EnumDeclaration enm = (EnumDeclaration) member;
        String enumName = enm.getName();
        ArrayList<String> name = new ArrayList<String>();
        name.add(enumName);

        ArrayList<String> entries = new ArrayList<String>();

        for (EnumConstantDeclaration e: enm.getEntries()) {
            entries.add(e.getName());
        }

        enumMap.put("name", name);
        enumMap.put("enumFields", entries);

        return enumMap;
    }
}

import java.util.*;

public class Employee {

  private int id;
  private String name;
  private int age;
  private String designation;
  private String experience;

  public void setId(int id){
    id = id;
  }

  public int getId(){
    return id;
  }

  public void setName(String name){
    name = name;
  }

  public String getName(){
    return name;
  }

  public void setAge(int age){
    age = age;
  }

  public int getAge(){
    return age;
  }

  public void setDesignation(String desig){
    designation = desig;
  }

  public int getDesignation(){
    return designation;
  }

  public void setExperience(int exp){
    experience = exp;
  }

  public int getExperience(){
    return experience;
  }

}

public class RegisterEmployee {

	public void addEmployee(String name, int age, String designation, int experience) {
		Employee empAccount = new Employee();
		empAccount.setId(1);
		empAccount.setName(name);
		empAccount.setAge(age);
		empAccount.setDesignation(designation);
		empAccount.setExperience(experience);
	}

}

public class Tester {
	public static void main(String[] args) {
		RegisterEmployee e = new RegisterEmployee();
		e.addEmployee("Sanjeedha", 25, "Software Engineer", 3);

	}
}

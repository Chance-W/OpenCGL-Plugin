package com.opencgl.template.model;

import java.util.Objects;
import java.util.Random;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/06/18 10:41
 * @since v9.0
 */
public class Person {
    private final String name;
    private final String surname;
    private int age;

    public Person(String name) {
        this.name = name;
        this.surname = "";
    }

    public Person(String name, String surname) {
        this.name = name;
        this.surname = surname;
    }

    public Person(String name, String surname, int age) {
        this.name = name;
        this.surname = surname;
        this.age = age;
    }

    public static Person ofSplit(String fullName, String split) {
        String[] fNameArray = fullName.split(split);
        return new Person(fNameArray[0], fNameArray[1]);
    }

    public String getName() {
        return name;
    }

    public String getSurname() {
        return surname;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public Person randomAge() {
        setAge(new Random().nextInt());
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return getName().equals(person.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }
}

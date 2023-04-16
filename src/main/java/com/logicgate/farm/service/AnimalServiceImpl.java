package com.logicgate.farm.service;

import com.logicgate.farm.domain.Animal;
import com.logicgate.farm.domain.Barn;
import com.logicgate.farm.domain.Color;
import com.logicgate.farm.repository.AnimalRepository;
import com.logicgate.farm.repository.BarnRepository;
import com.logicgate.farm.util.FarmUtils;
import com.logicgate.farm.util.ReverseListSizeComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class AnimalServiceImpl implements AnimalService {

  private final AnimalRepository animalRepository;

  private final BarnRepository barnRepository;

  @Autowired
  public AnimalServiceImpl(AnimalRepository animalRepository, BarnRepository barnRepository) {
    this.animalRepository = animalRepository;
    this.barnRepository = barnRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Animal> findAll() {
    return animalRepository.findAll();
  }

  @Override
  public void deleteAll() {
    animalRepository.deleteAll();
  }

  @Override
  public Animal addToFarm(Animal animal) {
    Animal currentAnimal = animal;
    List<Barn> barnByColorList = barnRepository.findAll()
        .stream()
        .filter(barnSearch -> barnSearch.getColor().equals(currentAnimal.getFavoriteColor()))
        .collect(Collectors.toList());

    if (barnByColorList.isEmpty()) {
      Barn newBarn = createNewBarn(animal.getFavoriteColor());
      animal.setBarn(newBarn);
      animalRepository.save(animal);
      return animal;
    }

    List<Animal> animalByColorList = animalRepository.findAll()
        .stream()
        .filter(animalColor -> animalColor.getFavoriteColor().equals(currentAnimal.getFavoriteColor()))
        .collect(Collectors.toList());

    animalByColorList.add(animal);
    if (Objects.isNull(animal.getBarn())) {
      animal.setBarn(barnByColorList.get(0));
    }

    animal = distributeAnimal(animal, barnByColorList);
    if ((animalByColorList.size() - 1) % FarmUtils.barnCapacity() == 0) {
      Barn newBarn = createNewBarn(animal.getFavoriteColor());
      animal.setBarn(newBarn);
      barnByColorList.add(newBarn);

      animal = distributeAnimal(animal, barnByColorList);
    }
    animalRepository.save(animal);
    return animal;
  }

  @Override
  public void addToFarm(List<Animal> animals) {
    animals.forEach(this::addToFarm);
  }

  @Override
  public void removeFromFarm(Animal animal) {
    List<Barn> barnByColorList = barnRepository.findAll()
        .stream()
        .filter(barnSearch -> barnSearch.getColor().equals(animal.getFavoriteColor()))
        .collect(Collectors.toList());
    List<Animal> animalByColorList = animalRepository.findAll()
        .stream()
        .filter(animalColor -> animalColor.getFavoriteColor().equals(animal.getFavoriteColor()))
        .collect(Collectors.toList());
    if ((animalByColorList.size() - 1) % FarmUtils.barnCapacity() == 0) {
      deleteDistributeAnimal(animal, barnByColorList);
    }
    animalRepository.deleteById(animal.getId());
  }

  @Override
  public void removeFromFarm(List<Animal> animals) {
    animals.forEach(animal -> removeFromFarm(animalRepository.getOne(animal.getId())));
  }

  private Barn createNewBarn(Color color) {
    Barn newBarn = new Barn(FarmUtils.randomBarnName().name(), color);
    barnRepository.save(newBarn);
    return newBarn;
  }

  private List<List<Animal>> getListOfAnimalsByBarn(Animal animal, List<Barn> barnByColorList, boolean addingAnimal) {
    List<List<Animal>> listOfAnimalsByBarn = new ArrayList<>();
    for (int i = 0; i < barnByColorList.size(); i++) {
      Barn currentBarn = barnByColorList.get(i);
      if (addingAnimal) {
        animal.setBarn(currentBarn);
      }
      List<Animal> animalsInBarnList = animalRepository.findAll()
          .stream()
          .filter(barnAnimal -> barnAnimal.getBarn().getId().equals(currentBarn.getId()))
          .collect(Collectors.toList());
      if (addingAnimal) {
        animalsInBarnList.add(animal);
      }
      listOfAnimalsByBarn.add(animalsInBarnList);
    }
    return listOfAnimalsByBarn;
  }

  private Animal distributeAnimal(Animal animal, List<Barn> barnByColorList) {
    List<List<Animal>> listOfAnimalsByBarn = getListOfAnimalsByBarn(animal, barnByColorList, true);

    for (int i = 0; i < listOfAnimalsByBarn.size() - 1; i++) {
      listOfAnimalsByBarn.get(i).remove(animal);
    }

    if (barnByColorList.size() > 1) {
      List<Animal> animalsByColorList = animalRepository.findAll()
          .stream()
          .filter(barnAnimal -> barnAnimal.getFavoriteColor().equals(animal.getFavoriteColor()))
          .collect(Collectors.toList());
      int barnTargetSize = (animalsByColorList.size() / barnByColorList.size()) + 1;

      listOfAnimalsByBarn.sort(new ReverseListSizeComparator());
      for (int i = 0; i < listOfAnimalsByBarn.size() - 1; i++) {
        List<Animal> currentBarn = listOfAnimalsByBarn.get(i);
        while (currentBarn.size() > barnTargetSize) {
          currentBarn.get(i).setBarn(listOfAnimalsByBarn.get(i + 1).get(0).getBarn());
          listOfAnimalsByBarn.get(i + 1).add(currentBarn.get(i));
          currentBarn.remove(i);
        }
        animalRepository.saveAll(currentBarn);
      }
    }
    return animal;
  }

  private Animal deleteDistributeAnimal(Animal animal, List<Barn> barnByColorList) {
    List<List<Animal>> listOfAnimalsByBarn = getListOfAnimalsByBarn(animal, barnByColorList, false);

    listOfAnimalsByBarn.sort(new ReverseListSizeComparator());
    if (listOfAnimalsByBarn.size() > 1) {
      for (int i = 0; i < listOfAnimalsByBarn.size(); i++) {
        List<Animal> currentBarn = listOfAnimalsByBarn.get(i);
        currentBarn.remove(animal);
        if (i == 0) {
          while (currentBarn.size() != 0 && currentBarn.size() <= FarmUtils.barnCapacity()) {
            currentBarn.get(i).setBarn(listOfAnimalsByBarn.get(i + 1).get(0).getBarn());
            listOfAnimalsByBarn.get(i + 1).add(currentBarn.get(i));
            currentBarn.remove(i);
          }
        }
        if (i != 0) {
          while (i < listOfAnimalsByBarn.size() - 1
                && currentBarn.size() != 0
                && !listOfAnimalsByBarn.get(i + 1).isEmpty()
                && currentBarn.size() != FarmUtils.barnCapacity()) {
            currentBarn.get(i).setBarn(listOfAnimalsByBarn.get(i + 1).get(i).getBarn());
            listOfAnimalsByBarn.get(i + 1).add(currentBarn.get(i));
            currentBarn.remove(i);
          }
        }
        if (i == listOfAnimalsByBarn.size() - 1) {
          animalRepository.saveAll(currentBarn);
        }
      }
    }

    for (int i = 0; i < listOfAnimalsByBarn.size(); i++) {
      Barn currentBarn = barnByColorList.get(i);
      List<Animal> animalsInBarnList = animalRepository.findAll()
          .stream()
          .filter(barnAnimal -> barnAnimal.getBarn().getId().equals(currentBarn.getId()))
          .collect(Collectors.toList());
      if (animalsInBarnList.size() == 0) {
        barnRepository.delete(currentBarn);
      }
    }
    return animal;
  }
}

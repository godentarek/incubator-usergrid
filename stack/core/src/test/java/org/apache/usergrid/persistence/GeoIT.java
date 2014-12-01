/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.usergrid.persistence;


import java.util.*;

import org.apache.usergrid.persistence.model.field.value.Location;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.usergrid.AbstractCoreIT;
import org.apache.usergrid.cassandra.Concurrent;
import org.apache.usergrid.persistence.geo.model.Point;
import org.apache.usergrid.persistence.index.query.Query;
import org.apache.usergrid.utils.MapUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


@Concurrent()
public class GeoIT extends AbstractCoreIT {
    private static final Logger LOG = LoggerFactory.getLogger( GeoIT.class );
    /*
      A list of concrete entities with locations to be used for geoQuery tests
      NOTE: Adding or removing items from this list could affect test outcome!!!
     */
    int NEARBY_RADIUS=10000;
    int CIRCUMFERENCE_OF_THE_EARTH=40000000;
    private static List<Map<String, Object>> LOCATION_PROPERTIES = new ArrayList<Map<String, Object>>();
    static {
      LOCATION_PROPERTIES.add(new LinkedHashMap<String, Object>() {{
        put( "name", "norwest" );
        put( "location", new LinkedHashMap<String, Object>() {{
          put("latitude", -33.746369 );
          put("longitude", 150.952183 );
        }} );
      }});
      LOCATION_PROPERTIES.add(new LinkedHashMap<String, Object>() {{
        put( "type", "store" );
        put( "name", "ashfield" );
        put( "location", new LinkedHashMap<String, Object>() {{
          put("latitude", -33.889058 );
          put("longitude", 151.124024 );
        }} );
      }});
    }
    public GeoIT() throws Exception{
        super();
//        loadGeolocationTestEntities();
    }


  @Test
  public void testGeo() throws Exception {
    LOG.info( "GeoIT.testGeo" );




    EntityManager em =  app.getEntityManager();
    assertNotNull( em );

    // create user at a location
    Map<String, Object> properties = new LinkedHashMap<String, Object>() {{
      put( "username", "edanuff" );
      put( "email", "ed@anuff.com" );
      put( "location", new LinkedHashMap<String, Object>() {{
        put("latitude", 37.776753 );
        put("longitude", -122.407846 );
      }} );
    }};

    Entity user = em.create( "user", properties );
    assertNotNull( user );

    em.refreshIndex();

    // define center point about 300m from that location
    Point center = new Point( 37.774277, -122.404744 );

    Query query = Query.fromQL( "select * where location within 200 of "
        + center.getLat() + "," + center.getLon());
    Results listResults = em.searchCollection( em.getApplicationRef(), "users", query );

    assertEquals("No results less than 200m away from center", 0, listResults.size() );

    query = Query.fromQL( "select * where location within 400 of "
        + center.getLat() + "," + center.getLon());
    listResults = em.searchCollection( em.getApplicationRef(), "users", query );

    this.dump( listResults );

    assertEquals("1 result less than 400m away from center", 1, listResults.size() );

    // remove location from user
    properties.remove("location");
    em.updateProperties(user, properties);
    em.refreshIndex();

    query = Query.fromQL( "select * where location within 400 of "
        + center.getLat() + "," + center.getLon());
    listResults = em.searchCollection( em.getApplicationRef(), "users", query );

    this.dump( listResults );

    // user no longer found with 400m search
    assertEquals( 0, listResults.size() );

    // move user and center to new locations
    updatePos( em, user, 37.426373, -122.14108 );

    center = new Point( 37.774277, -122.404744 );

    query = Query.fromQL( "select * where location within 200 of "
        + center.getLat() + "," + center.getLon());
    listResults = em.searchCollection( em.getApplicationRef(), "users", query );

    assertEquals( 0, listResults.size() );

    updatePos( em, user, 37.774277, -122.404744 );

    center = new Point( 37.776753, -122.407846 );

    query = Query.fromQL( "select * where location within 1000 of "
        + center.getLat() + "," + center.getLon());
    listResults = em.searchCollection( em.getApplicationRef(), "users", query );

    assertEquals( 1, listResults.size() );

    // check at globally large distance

    query = Query.fromQL( "select * where location within " + Integer.MAX_VALUE + " of "
        + center.getLat() + "," + center.getLon());
    listResults = em.searchCollection( em.getApplicationRef(), "users", query );

    assertEquals( 1, listResults.size() );

    // create a new entity so we have 2
    LinkedHashMap<String, Object> properties2 = new LinkedHashMap<String, Object>() {{
      put( "username", "sganyo" );
      put( "email", "sganyo@anuff.com" );
      put( "location", new LinkedHashMap<String, Object>() {{
        put("latitude", 31.1 );
        put("longitude", 121.2 );
      }} );
    }};
    Entity user2 = em.create( "user", properties2 );
    em.refreshIndex();
    assertNotNull( user2 );

    query = Query.fromQL( "select * where location within 10000 of "
        + center.getLat() + "," + center.getLon());
    listResults = em.searchCollection( em.getApplicationRef(), "users", query );

    assertEquals( 1, listResults.size() );

    // check at globally large distance
    query = Query.fromQL( "select * where location within " + Integer.MAX_VALUE + " of "
        + center.getLat() + "," + center.getLon());
    listResults = em.searchCollection( em.getApplicationRef(), "users", query );

    assertEquals( 2, listResults.size() );

    // check at globally large distance (center point close to other entity)
    center = new Point( 31.14, 121.27 );

    query = Query.fromQL( "select * where location within " + Integer.MAX_VALUE + " of "
        + center.getLat() + "," + center.getLon());
    listResults = em.searchCollection( em.getApplicationRef(), "users", query );

    assertEquals( 2, listResults.size() );

    Results emSearchResults = em.searchCollection( em.getApplicationRef(), "users",
        Query.fromQL( "location within 1000 of 37.776753, -122.407846" ) );
    assertEquals( 1, emSearchResults.size() );

    updatePos( em, user, 37.776753, -122.407846 );

    center = new Point( 37.428526, -122.140916 );

    query = Query.fromQL( "select * where location within 1000 of "
        + center.getLat() + "," + center.getLon());
    listResults = em.searchCollection( em.getApplicationRef(), "users", query );


    assertEquals( 0, listResults.size() );

    emSearchResults = em.searchCollection( em.getApplicationRef(), "users",
        Query.fromQL( "location within 1000 of 37.428526, -122.140916" ) );
    assertEquals( 0, emSearchResults.size() );

    properties = new LinkedHashMap<String, Object>();
    properties.put( "name", "Brickhouse" );
    properties.put( "address", "426 Brannan Street" );
    properties.put( "location", getLocation( 37.779632, -122.395131 ) );

    Entity restaurant = em.create( "restaurant", properties );
    assertNotNull( restaurant );

    em.createConnection( user, "likes", restaurant );

    em.refreshIndex();

    emSearchResults = em.searchConnectedEntities( user,
        Query.fromQL( "location within 2000 of 37.776753, -122.407846" ).setConnectionType( "likes" ) );
    assertEquals( 1, emSearchResults.size() );

    emSearchResults = em.searchConnectedEntities( user,
        Query.fromQL( "location within 1000 of 37.776753, -122.407846" ).setConnectionType( "likes" ) );
    assertEquals( 0, emSearchResults.size() );
  }

  /**
   * Load entities for geo queries
   * 1. Get an instance of the entity manager
   * 2. load test entities
   * 3. refresh the index
   * 4. return the entity manager
   */
  private EntityManager loadGeolocationTestEntities() throws Exception{
    LOG.info( "GeoIT.loadGeolocationTestEntities" );
    //1. Get an instance of the entity manager

    EntityManager em =  app.getEntityManager();
    assertNotNull( em );
    //2. load test entities
    for (Map<String, Object> location : LOCATION_PROPERTIES) {
      LOG.info( "Create entity with location '{}'", location.get("name"));
      Entity entity = em.create("store", location);
      assertNotNull(entity);
    }
    //3. refresh the index
    em.refreshIndex();
    //4. return the entity manager
    return em;
  }
  /**
   * Validate loaded entities for geo queries
   * 1. load test entities
   * 2. validate the size of the result
   * 3. verify each entity has geo data
   */
  @Test
  public void testGeolocationEntities() throws Exception{
//    1. load test entities
    EntityManager em =  app.getEntityManager();
    assertNotNull( em );
    //2. load test entities
    for(Map<String, Object> location: LOCATION_PROPERTIES){
      Entity entity = em.create( "store", location );
      assertNotNull( entity );
      LOG.debug("Entity {} created", entity.getProperty("name"));
    }
    em.refreshIndex();
//    2. validate the size of the result
    Query query = new Query();
    Results listResults = em.searchCollection( em.getApplicationRef(), "stores", query );
    assertEquals("total number of 'stores'", LOCATION_PROPERTIES.size(), listResults.size() );
//    3. verify each entity has geo data
    for(Entity entity: listResults.entities){
      Location location=(Location)entity.getProperty("location");
      assertNotNull(location);
    }

  }
  @Test
  /**
   * Load entities with location data and query them from a far away location
   * 1. create entities with geo
   * 2. Query the collection from a point more than 10000m from the locations
   *    and ensure that no entities are returned when restricted to a 10000m radius
   * 3. Query the collection from a point more than 10000m from the locations
   *    and ensure that all entities are returned when the distance is set to the
   *    circumference of the earth
   */
  public void testGeoFromFarAwayLocation() throws Exception {
    //1
    EntityManager em = loadGeolocationTestEntities();
    //2
    Point center = new Point( 37.776753, -122.407846 );
    Query query = Query.fromQL( "select * where location within "+NEARBY_RADIUS+" of "
        + center.getLat() + "," + center.getLon());
    Results listResults = em.searchCollection( em.getApplicationRef(), "stores", query );

    assertEquals("Results within "+NEARBY_RADIUS+"m from center", 0, listResults.size() );
    //3
    Query query2 = Query.fromQL( "select * where location within "+CIRCUMFERENCE_OF_THE_EARTH+" of "
        + center.getLat() + "," + center.getLon());
    listResults = em.searchCollection( em.getApplicationRef(), "stores", query2 );

    assertEquals("Results within "+CIRCUMFERENCE_OF_THE_EARTH+"m from center", LOCATION_PROPERTIES.size(), listResults.size() );

  }
  @Test
  /**
   * Load entities with location data and query them from a nearby location
   * 1. create entities with geo
   * 2. Query the collection from a point less than 10000m from the locations
   *    and ensure that one entity is returned when restricted to a 10000m radius
   * 3. Query the collection from a point less than 10000m from the locations
   *    and ensure that all entities are returned when the distance is set to the
   *    circumference of the earth
   */
  public void testGeoFromNearbyLocation() throws Exception {
    LOG.info( "GeoIT.testGeoFromNearbyLocation" );
    //1
    EntityManager em = loadGeolocationTestEntities();

    Point center = new Point( -33.746369, 150.952185 );
    Query query = Query.fromQL( "select * where location within "+NEARBY_RADIUS+" of "
        + center.getLat() + "," + center.getLon());
    Results listResults = em.searchCollection( em.getApplicationRef(), "stores", query );

    assertEquals("Results within "+NEARBY_RADIUS+"m from center", 1, listResults.size() );
    //3
    query = Query.fromQL( "select * where location within "+CIRCUMFERENCE_OF_THE_EARTH+" of "
        + center.getLat() + "," + center.getLon());
    listResults = em.searchCollection( em.getApplicationRef(), "stores", query );

    assertEquals("Results within "+CIRCUMFERENCE_OF_THE_EARTH+"m from center", LOCATION_PROPERTIES.size(), listResults.size() );
  }
  /**
   * Load entities with location data and query them from multiple locations
   * to ensure proper bounds
   * 1. Create entities with geo
   * 2. Create a list of points from different geographic areas
   * 3. Query the collection from each point
   *    and ensure that no entities are returned when restricted to a 10000m radius
   * 4. Query the collection from each point
   *    and ensure that all entities are returned when the distance is set to the
   *    circumference of the earth
   */

  @Test
  public void testGeoFromMultipleLocations() throws Exception {
    LOG.info( "GeoIT.testGeoFromMultipleLocations" );
    //1 Create entities with geo
    EntityManager em = loadGeolocationTestEntities();
    //2 Create a list of points from different geographic areas
    List<Point> points = new ArrayList<Point>();
    points.add(new Point( -90.000000, 90.000000 ));//Antarctica
    points.add(new Point( 90, 90 ));//Santa's house
    points.add(new Point( 33.746369,-89 ));//Woodland, MS
    points.add(new Point( 34.35, 58.22)); //Buenos Aires
    points.add(new Point( 39.55,116.25));//Beijing, China
    points.add(new Point( 44.52,20.32)); //Belgrade, Serbia
    points.add(new Point( -1.000000, 102.000000 ));//Somewhere in Indonesia
    for(Point center:points){
      //3 Query the collection from each point
      //  and ensure that no entities are returned when restricted to a 10000m radius
      Query query = Query.fromQL( "select * where location within 10000 of "
          + center.getLat() + "," + center.getLon());
      Results listResults = em.searchCollection( em.getApplicationRef(), "stores", query );
      assertEquals("Results less than 10000m away from center", 0, listResults.size() );
      //4 Query the collection from each point
      //  and ensure that all entities are returned when the distance is set to the
      //  circumference of the earth
      Query query2 = Query.fromQL( "select * where location within 40000000 of "
          + center.getLat() + "," + center.getLon());
      listResults = em.searchCollection( em.getApplicationRef(), "stores", query2 );
      assertEquals("Results from center point to ridiculously far", LOCATION_PROPERTIES.size(), listResults.size() );
    }
  }
  @Test
  public void testGeoBadPoints() throws Exception {
    LOG.info( "GeoIT.testGeoBadPoints" );
    double[][] vertices= {
        {-91.000000, 90.000000},
        {91.000000, 90.000000},
        {90.000000, 400},
        {90.000000, -270.000000},
        {-91.000000, -91.000000}
    };
    for (int i=0;i<vertices.length;i++){
      //bad coordinate. bad! you're supposed to have lat between -90 and 90
      try {
        Point p = new Point(vertices[i][0], vertices[i][1]);
        assertTrue("Bad points should throw an exception ["+vertices[i][0]+","+vertices[i][1]+"]", false);
      }catch(java.lang.IllegalArgumentException e){
        assertTrue("Bad points should throw an exception ["+vertices[i][0]+","+vertices[i][1]+"]" , true);
      }
    }



  }


  @Test
    public void testPointPaging() throws Exception {


        EntityManager em = app.getEntityManager();
        assertNotNull( em );

        // save objects in a diagonal line from -90 -180 to 90 180

        int numEntities = 500;

        float minLattitude = -90;
        float maxLattitude = 90;
        float minLongitude = -180;
        float maxLongitude = 180;

        float lattitudeDelta = ( maxLattitude - minLattitude ) / numEntities;

        float longitudeDelta = ( maxLongitude - minLongitude ) / numEntities;

        for ( int i = 0; i < numEntities; i++ ) {
            float lattitude = minLattitude + lattitudeDelta * i;
            float longitude = minLongitude + longitudeDelta * i;

            Map<String, Float> location = MapUtils.hashMap( "latitude", lattitude ).map( "longitude", longitude );

            Map<String, Object> data = new HashMap<String, Object>( 2 );
            data.put( "name", String.valueOf( i ) );
            data.put( "location", location );

            em.create( "store", data );
        }

        em.refreshIndex();

        Query query = new Query();
        // earth's circumference is 40,075 kilometers. Up it to 50,000kilometers
        // just to be save
        query.addFilter( "location within 50000000 of -90, -180" );
        query.setLimit( 100 );

        int count = 0;
        Results results;

        do {
            results = em.searchCollection( em.getApplicationRef(), "stores", query );

            for ( Entity entity : results.getEntities() ) {
                assertEquals( String.valueOf( count ), entity.getName() );
                count++;
            }

            // set for the next "page"
            query.setCursor( results.getCursor() );
        }
        while ( results.getCursor() != null );

        // check we got back all 500 entities
        assertEquals( numEntities, count );
    }


    @Test
    public void testSamePointPaging() throws Exception {

        EntityManager em =  app.getEntityManager();
        assertNotNull( em );

        // save objects in a diagonal line from -90 -180 to 90 180

        int numEntities = 500;

        for ( int i = 0; i < numEntities; i++ ) {
            Map<String, Object> data = new HashMap<String, Object>( 2 );
            data.put( "name", String.valueOf( i ) );
            setPos( data, 0, 0 );

            em.create( "store", data );
        }

        em.refreshIndex();

        Query query = new Query();
        // earth's circumference is 40,075 kilometers. Up it to 50,000kilometers
        // just to be save
        query.addFilter( "location within 50000000 of 0, 0" );
        query.setLimit( 100 );

        int count = 0;
        Results results;

        do {
            results = em.searchCollection( em.getApplicationRef(), "stores", query );

            for ( Entity entity : results.getEntities() ) {
                assertEquals( String.valueOf( count ), entity.getName() );
                count++;
            }

            // set for the next "page"
            query.setCursor( results.getCursor() );
        }
        while ( results.getCursor() != null );

        // check we got back all 500 entities
        assertEquals( numEntities, count );
    }

    @Test
    public void testDistanceByLimit() throws Exception {



        EntityManager em =  app.getEntityManager();
        assertNotNull( em );

        // save objects in a diagonal line from -90 -180 to 90 180

        int numEntities = 100;

        float minLattitude = -90;
        float maxLattitude = 90;
        float minLongitude = -180;
        float maxLongitude = 180;

        float lattitudeDelta = ( maxLattitude - minLattitude ) / numEntities;

        float longitudeDelta = ( maxLongitude - minLongitude ) / numEntities;

        for ( int i = 0; i < numEntities; i++ ) {
            float lattitude = minLattitude + lattitudeDelta * i;
            float longitude = minLongitude + longitudeDelta * i;

            Map<String, Float> location = MapUtils.hashMap( "latitude", lattitude ).map( "longitude", longitude );

            Map<String, Object> data = new HashMap<String, Object>( 2 );
            data.put( "name", String.valueOf( i ) );
            data.put( "location", location );

            em.create( "store", data );
        }

        em.refreshIndex();

        Query query = new Query();
        // earth's circumference is 40,075 kilometers. Up it to 50,000kilometers
        // just to be save
        query.addFilter( "location within 50000000 of -90, -180" );
        query.setLimit( 100 );

        int count = 0;

        do {
            Results results = em.searchCollection( em.getApplicationRef(), "stores", query );

            for ( Entity entity : results.getEntities() ) {
                assertEquals( String.valueOf( count ), entity.getName() );
                count++;
            }
        }
        while ( query.getCursor() != null );

        // check we got back all 500 entities
        assertEquals( numEntities, count );
    }


    @Test
    public void testGeoWithIntersection() throws Exception {

        EntityManager em = app.getEntityManager();
        assertNotNull( em );

        int size = 100;
        int min = 50;
        int max = 90;

        List<Entity> created = new ArrayList<Entity>( size );

        for ( int i = 0; i < size; i++ ) {

            // save all entities in the same location
            Map<String, Object> data = new HashMap<String, Object>( 2 );
            data.put( "name", String.valueOf( i ) );
            data.put( "index", i );
            setPos( data, 0, 0 );

            Entity e = em.create( "store", data );

            created.add( e );
        }

        em.refreshIndex();

        int startDelta = size - min;

        //    String queryString = String.format("select * where location within 100 of 0,
        // 0 and index >= %d and index < %d order by index",min, max);

        String queryString = String.format( "select * where index >= %d and index < %d order by index", min, max );

        Query query = Query.fromQL( queryString );

        Results r;
        int count = 0;

        do {

            r = em.searchCollection( em.getApplicationRef(), "stores", query );

            for ( Entity e : r.getEntities() ) {
                assertEquals( created.get( startDelta + count ), e );
                count++;
            }

            query.setCursor( r.getCursor() );
        }
        while ( r.hasCursor() );

        assertEquals( startDelta - ( size - max ), count );
    }


    @Test
    public void testDenseSearch() throws Exception {

        EntityManager em =  app.getEntityManager();
        assertNotNull( em );

        // save objects in a diagonal line from -90 -180 to 90 180

        int numEntities = 250;

        float minLatitude = 48.32455f;
        float maxLatitude = 48.46481f;
        float minLongitude = 9.89561f;
        float maxLongitude = 10.0471f;

        float latitudeDelta = ( maxLatitude - minLatitude ) / numEntities;

        float longitudeDelta = ( maxLongitude - minLongitude ) / numEntities;

        for ( int i = 0; i < numEntities; i++ ) {
            float latitude = minLatitude + latitudeDelta * i;
            float longitude = minLongitude + longitudeDelta * i;

            Map<String, Float> location = MapUtils.hashMap( "latitude", latitude ).map( "longitude", longitude );

            Map<String, Object> data = new HashMap<String, Object>( 2 );
            data.put( "name", String.valueOf( i ) );
            data.put( "location", location );

            em.create( "store", data );
        }

        em.refreshIndex();

        //do a direct geo iterator test.  We need to make sure that we short circuit on the correct tile.

        float latitude = 48.38626f;
        float longitude = 9.94175f;
        int distance = 1000;
        int limit = 8;

        {
            // QuerySlice slice = new QuerySlice( "location", 0 );
            // GeoIterator itr = new GeoIterator( new CollectionGeoSearch( 
            //     em, setup.getIbl(), setup.getCassSvc(), em.getApplicationRef(), "stores" ),
            //     limit, slice, "location", new Point( lattitude, longitude ), distance );
            //
            // // check we got back all 500 entities
            // assertFalse( itr.hasNext() );
            //
            // List<String> cells = itr.getLastCellsSearched();
            // assertEquals( 1, cells.size() );
            // assertEquals( 4, cells.get( 0 ).length() );

        }

        {
            long startTime = System.currentTimeMillis();

            //now test at the EM level, there should be 0 results.
            Query query = new Query();

            query.addFilter( "location within 1000 of 48.38626, 9.94175" ); // lat, lon 
            query.setLimit( limit );

            Results results = em.searchCollection( em.getApplicationRef(), "stores", query );

            assertEquals( 0, results.size() );

            long endTime = System.currentTimeMillis();

            LOG.info( "Runtime took {} milliseconds to search", endTime - startTime );
        }
    }


    public Map<String, Object> getLocation( double latitude, double longitude ) throws Exception {
        Map<String, Object> latlong = new LinkedHashMap<String, Object>();
        latlong.put( "latitude", latitude );
        latlong.put( "longitude", longitude );
        return latlong;
    }


    public void updatePos( EntityManager em, EntityRef entity, double latitude, double longitude ) throws Exception {
        Map<String, Object> latlong = new LinkedHashMap<String, Object>();
        latlong.put( "latitude", latitude );
        latlong.put( "longitude", longitude );

        em.setProperty( entity, "location", latlong );
        em.refreshIndex();
    }


    public void setPos( Map<String, Object> data, double latitude, double longitude ) {
        Map<String, Object> latlong = new LinkedHashMap<String, Object>();
        latlong.put( "latitude", latitude );
        latlong.put( "longitude", longitude );

        data.put( "location", latlong );
    }
}

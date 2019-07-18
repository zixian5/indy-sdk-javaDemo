extern crate digest;
extern crate indy_crypto;
extern crate sha2;
extern crate rust_base58;
extern crate thread_scoped;
extern crate time;

use errors::prelude::*;
use services::blob_storage::BlobStorageService;
use domain::anoncreds::revocation_registry_definition::RevocationRegistryDefinitionV1;

use self::indy_crypto::cl::{Tail, RevocationTailsAccessor, RevocationTailsGenerator};
use self::indy_crypto::errors::prelude::{IndyCryptoError, IndyCryptoErrorKind};
use self::indy_crypto::pair::*;

use self::rust_base58::{ToBase58, FromBase58};

use std::rc::Rc;
use std::sync::{Arc, Mutex};
use std::thread;
use std::collections::HashMap;
use time::*;

const TAILS_BLOB_TAG_SZ: u8 = 2;
const TAIL_SIZE: usize = Tail::BYTES_REPR_SIZE;

pub struct SDKTailsAccessor {
    tails_service: Rc<BlobStorageService>,
    tails_reader_handle: i32,
}

impl SDKTailsAccessor {
    pub fn new(tails_service: Rc<BlobStorageService>,
               tails_reader_handle: i32,
               rev_reg_def: &RevocationRegistryDefinitionV1) -> IndyResult<SDKTailsAccessor> {
        let tails_hash = rev_reg_def.value.tails_hash.from_base58()
            .map_err(|_| err_msg(IndyErrorKind::InvalidState, "Invalid base58 for Tails hash"))?;

        let tails_reader_handle = tails_service.open_blob(tails_reader_handle,
                                                          &rev_reg_def.value.tails_location,
                                                          tails_hash.as_slice())?;

        Ok(SDKTailsAccessor {
            tails_service,
            tails_reader_handle
        })
    }
}

impl Drop for SDKTailsAccessor {
    fn drop(&mut self) {
        #[allow(unused_must_use)] //TODO
            {
                self.tails_service.close(self.tails_reader_handle)
                    .map_err(map_err_err!());
            }
    }
}

impl RevocationTailsAccessor for SDKTailsAccessor {
    fn access_tail(&self, tail_id: u32, accessor: &mut FnMut(&Tail)) -> Result<(), IndyCryptoError> {
        debug!("access_tail >>> tail_id: {:?}", tail_id);

        let tail_bytes = self.tails_service
            .read(self.tails_reader_handle,
                  TAIL_SIZE,
                  TAIL_SIZE * tail_id as usize + TAILS_BLOB_TAG_SZ as usize)
            .map_err(|_|
                IndyCryptoError::from_msg(IndyCryptoErrorKind::InvalidState, "Can't read tail bytes from blob storage"))?; // FIXME: IO error should be returned

        let tail = Tail::from_bytes(tail_bytes.as_slice())?;
        accessor(&tail);

        let res = ();
        debug!("access_tail <<< res: {:?}", res);
        Ok(res)
    }
}

// pub fn store_tails_from_generator(service: Rc<BlobStorageService>,
//                                   writer_handle: i32,
//                                   rtg: &mut RevocationTailsGenerator) -> IndyResult<(String, String)> {
//     debug!("store_tails_from_generator >>> writer_handle: {:?}", writer_handle);

//     let blob_handle = service.create_blob(writer_handle)?;

//     let version = vec![0u8, TAILS_BLOB_TAG_SZ];
//     service.append(blob_handle, version.as_slice())?;

//     let start = time::now();
//     let mut st0:time::Tm = time::now();
//     // let mut totalTime:i32 =0;
//    // println!("the size is {:?}", rtg.size);
//     while let Some(tail) = rtg.next()? {
// 	// let ed = time::now();
// 	// println!("The {}th calculate tail need {:?}", totalTime,ed-st0);
//         let tail_bytes = tail.to_bytes()?;
// 	// let st = time::now();
//         service.append(blob_handle, tail_bytes.as_slice())?;
//         // let en = time::now();
// 	// println!("The {}th append to file {:?}", totalTime,en-st);
// 	// totalTime=totalTime+1;
// 	// st0 = time::now();
//     }
//     let end = time::now();
//     println!("generate tails total time {:?}", end-start);

//     let res = service.finalize(blob_handle).map(|(location, hash)| (location, hash.to_base58()))?;

//     debug!("store_tails_from_generator <<< res: {:?}", res);
//     Ok(res)
// }

pub struct TailAndIndex{
    tail : Vec<u8>,
    ind : u32
}

pub fn store_tails_from_generator(service: Rc<BlobStorageService>,
                                  writer_handle: i32,
                                  rtg: &RevocationTailsGenerator) -> IndyResult<(String, String)> {
    debug!("store_tails_from_generator >>> writer_handle: {:?}", writer_handle);

    let blob_handle = service.create_blob(writer_handle)?;

    let version = vec![0u8, TAILS_BLOB_TAG_SZ];
    service.append(blob_handle, version.as_slice())?;

    let mut vec:Vec<TailAndIndex> = Vec::new();
    let mut tails_data = Arc::new(Mutex::new(vec));
    let mut thread_handlers = Vec::new();

    let size = rtg.getSize() ;
    let mut index = 0;
    unsafe{
    for i in 0..size
    {
        index = i;
        let mut tails_data = tails_data.clone();
        thread_handlers.push(thread_scoped::scoped( move || {
            match rtg.generate_tail(index){
                Ok(v) => {
                    let tail = v.unwrap();

                    match tail.to_bytes(){
                        Ok(v) => {
                            let mut tail_bytes = v;
                            let mut tails_data=tails_data.lock().unwrap();
                            let t = TailAndIndex{ tail:tail_bytes,ind:index};
                            tails_data.push(t);
                        }
                        Err(e) => {
                            println!("error parsing header: {:?}", e);
                        } 
                    }
                }   
                 Err(e) => {
                     println!("error parsing header: {:?}", e);
                }
            }    
        }))
    }
    }

    for handler in thread_handlers {
        handler.join();
    }

    let mut tails_data_tmp = tails_data.clone();
    let mut tails_data_tp= tails_data_tmp.lock().unwrap();
    let mut map:HashMap<u32, Vec<u8>> = HashMap::new();

    let size_tmp = size as usize;
    for i in 0..size_tmp{
        let tail_and_index :&TailAndIndex = &tails_data_tp[i];
        map.insert(tail_and_index.ind,tail_and_index.tail.clone());
    }

    for i in 0..size
    {
        let tail_bytes = map.get(&i).unwrap();
        service.append(blob_handle, tail_bytes.as_slice())?;
    }


    let res = service.finalize(blob_handle).map(|(location, hash)| (location, hash.to_base58()))?;

    debug!("store_tails_from_generator <<< res: {:?}", res);
    Ok(res)
}


